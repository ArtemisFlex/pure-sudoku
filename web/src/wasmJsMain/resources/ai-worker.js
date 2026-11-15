// Loads PureGames inside a Web Worker, where the computer opponents do their searches.
// The bundle's loader expects a page, so give it the little it reads: where the scripts live.
self.window = self;
self.document = {
  baseURI: self.location.href,
  currentScript: null,
  getElementsByTagName: () => [],
};
importScripts('puregames.js');
