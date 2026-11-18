"""Push the Google Play store listing (and optionally a build) from play-listing/.

    python tools/play_publish.py                 # check only: stage everything, validate, discard
    python tools/play_publish.py --commit        # stage and commit the listing
    python tools/play_publish.py --aab PATH --commit   # also upload a build to Production (draft)

No key file: signs in as the play-publisher service account by impersonation, using the
gcloud account already logged in on this PC (it needs Service Account Token Creator on the
service account). Text comes from play-listing/LISTING.md, images from play-listing/.
"""
import argparse, json, os, re, shutil, subprocess, sys, urllib.request

from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = 'app.puregames'
SERVICE_ACCOUNT = 'play-publisher@puregames-play.iam.gserviceaccount.com'
LANG = 'en-US'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KIT = os.path.join(ROOT, 'play-listing')


def access_token():
    """A one-hour androidpublisher token for the service account, minted with the gcloud user's rights."""
    gcloud = shutil.which('gcloud') or shutil.which('gcloud.cmd')
    user = subprocess.run([gcloud, 'auth', 'print-access-token'], capture_output=True, text=True, check=True).stdout.strip()
    req = urllib.request.Request(
        f'https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/{SERVICE_ACCOUNT}:generateAccessToken',
        data=json.dumps({'scope': ['https://www.googleapis.com/auth/androidpublisher'], 'lifetime': '3600s'}).encode(),
        headers={'Authorization': f'Bearer {user}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as r:
            return json.load(r)['accessToken']
    except urllib.error.HTTPError as e:
        sys.exit(f'Could not act as {SERVICE_ACCOUNT} ({e.code}): {e.read().decode()[:400]}\n'
                 'Check: Service Account Token Creator granted to you, and the IAM Service Account Credentials API enabled.')


def listing_text():
    md = open(os.path.join(KIT, 'LISTING.md'), encoding='utf-8').read()
    blocks = [b.strip() for b in re.findall(r'```\n(.*?)```', md, re.S)]
    notes = re.search(r'<en-US>\n(.*?)\n</en-US>', md, re.S).group(1).strip()
    title, short, full = blocks[0], blocks[1], blocks[2]
    assert len(title) <= 30 and len(short) <= 80 and len(full) <= 4000 and len(notes) <= 500
    return title, short, full, notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--commit', action='store_true', help='make the changes live in Play Console (otherwise check only)')
    ap.add_argument('--aab', help='also upload this .aab as a draft release (see --track)')
    ap.add_argument('--track', default='production', help='track for --aab (default production)')
    ap.add_argument('--no-images', action='store_true')
    ap.add_argument("--not-for-review", action="store_true", help="commit without sending for review")
    a = ap.parse_args()

    api = build('androidpublisher', 'v3', credentials=Credentials(access_token()), cache_discovery=False)
    edits = api.edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute()['id']
    kw = dict(packageName=PACKAGE, editId=edit)
    title, short, full, notes = listing_text()
    try:
        edits.details().update(**kw, body={'defaultLanguage': LANG, 'contactWebsite': 'https://puregames.app', 'contactEmail': 'support@puregames.app'}).execute()
        edits.listings().update(**kw, language=LANG, body={'language': LANG, 'title': title, 'shortDescription': short, 'fullDescription': full}).execute()
        print(f'listing: "{title}" | short {len(short)} chars | full {len(full)} chars')

        if not a.no_images:
            shots = sorted(f for f in os.listdir(os.path.join(KIT, 'phone-screenshots')) if f.endswith('.png'))
            uploads = [('icon', ['icon-512.png']), ('featureGraphic', ['feature-graphic-1024x500.png']),
                       ('phoneScreenshots', [os.path.join('phone-screenshots', f) for f in shots])]
            for kind, files in uploads:
                edits.images().deleteall(**kw, language=LANG, imageType=kind).execute()
                for f in files:
                    edits.images().upload(**kw, language=LANG, imageType=kind,
                                          media_body=MediaFileUpload(os.path.join(KIT, f), mimetype='image/png')).execute()
                print(f'images: {kind} x{len(files)}')

        if a.aab:
            print('uploading bundle (about 45 MB)...')
            b = edits.bundles().upload(**kw, media_body=MediaFileUpload(a.aab, mimetype='application/octet-stream', resumable=True)).execute()
            code = b['versionCode']
            edits.tracks().update(**kw, track=a.track, body={'track': a.track, 'releases': [{
                'name': str(code), 'versionCodes': [str(code)], 'status': 'draft',
                'releaseNotes': [{'language': LANG, 'text': notes}]}]}).execute()
            print(f'bundle: version code {code} -> {a.track} (draft)')

        if a.commit:
            edits.commit(**kw, changesNotSentForReview=a.not_for_review or None).execute()
            print('COMMITTED: changes are saved in Play Console.')
        else:
            edits.validate(**kw).execute()
            edits.delete(**kw).execute()
            print('CHECK ONLY: everything validated; nothing was saved. Re-run with --commit.')
    except Exception:
        try: edits.delete(**kw).execute()
        except Exception: pass
        raise


if __name__ == '__main__':
    main()
