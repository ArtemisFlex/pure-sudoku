// PureGames online play: a direct connection between two devices (WebRTC data channel).
// The same file runs in the browser and inside a hidden WebView on Android. It only sets up the link;
// the two devices swap their connection details (offer and answer) through the room-code service.
// Every event goes to one listener as (type, data): offer, answer, open, msg, close, fail, error.
(function () {
  const ICE = [
    { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] },
    { urls: 'stun:stun.cloudflare.com:3478' },
  ];
  let pc = null, dc = null;
  let emit = function () {};

  function wire(ch) {
    dc = ch;
    ch.onopen = () => emit('open', '');
    ch.onclose = () => emit('close', '');
    ch.onmessage = (e) => emit('msg', String(e.data));
  }

  // Gather every route to this device before sending the details, so one swap is enough.
  function gathered() {
    return new Promise((done) => {
      if (pc.iceGatheringState === 'complete') return done();
      const t = setTimeout(done, 4000);
      pc.addEventListener('icegatheringstatechange', () => {
        if (pc && pc.iceGatheringState === 'complete') { clearTimeout(t); done(); }
      });
    });
  }

  function fresh() {
    close();
    pc = new RTCPeerConnection({ iceServers: ICE });
    pc.onconnectionstatechange = () => { if (pc && pc.connectionState === 'failed') emit('fail', ''); };
  }

  async function host() {
    fresh();
    wire(pc.createDataChannel('pg', { ordered: true }));
    await pc.setLocalDescription(await pc.createOffer());
    await gathered();
    return pc.localDescription.sdp;
  }

  async function join(offer) {
    fresh();
    pc.ondatachannel = (e) => wire(e.channel);
    await pc.setRemoteDescription({ type: 'offer', sdp: offer });
    await pc.setLocalDescription(await pc.createAnswer());
    await gathered();
    return pc.localDescription.sdp;
  }

  function send(text) {
    if (dc && dc.readyState === 'open') { dc.send(text); return true; }
    return false;
  }

  function close() {
    try { if (dc) dc.close(); } catch (e) {}
    try { if (pc) pc.close(); } catch (e) {}
    dc = null; pc = null;
  }

  const fail = (e) => emit('error', String(e && e.message || e));
  globalThis.PgRtc = {
    setListener(f) { emit = f; },
    supported() { return typeof RTCPeerConnection !== 'undefined'; },
    host() { if (typeof RTCPeerConnection === 'undefined') return fail('unsupported'); host().then((s) => emit('offer', s)).catch(fail); },
    join(offer) { if (typeof RTCPeerConnection === 'undefined') return fail('unsupported'); join(offer).then((s) => emit('answer', s)).catch(fail); },
    answer(sdp) { if (pc) pc.setRemoteDescription({ type: 'answer', sdp }).catch(fail); },
    send,
    close,
  };
})();
