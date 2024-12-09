'use strict';

var core = require('@capacitor/core');

const MediaControl = core.registerPlugin('MediaControl', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => new m.MediaControlWeb()),
});

class MediaControlWeb extends core.WebPlugin {
    play(options) {
        return options;
    }
    load(options) {
        return Promise.resolve(options);
    }
    isLoaded() {
        return Promise.resolve({ value: false, url: "" });
    }
    playLoaded() {
        return;
    }
    isPlaying() {
        return Promise.resolve({ value: false });
    }
    pause() {
        return;
    }
    resume() {
        return;
    }
    stop() {
        return;
    }
    seek(options) {
        return options;
    }
    speed(options) {
        return options;
    }
    add(audioArray) {
        return audioArray;
    }
    fetchPlaylist() {
        throw new Error('Method not implemented.');
    }
    clearPlaylist() {
        return;
    }
    getUpdate() {
        throw new Error('Method not implemented.');
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    MediaControlWeb: MediaControlWeb
});

exports.MediaControl = MediaControl;
//# sourceMappingURL=plugin.cjs.js.map
