import { WebPlugin } from '@capacitor/core';
export class MediaControlWeb extends WebPlugin {
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
    fetchPlaylist(options) {
        return options;
    }
    clearPlaylist() {
        return;
    }
    getDuration(options) {
        return Promise.resolve(options);
    }
    getUpdate() {
        throw new Error('Method not implemented.');
    }
    retrieveStoredProgress() {
        throw new Error('Method not implemented.');
    }
    clearStoredProgress() {
        throw new Error('Method not implemented.');
    }
}
//# sourceMappingURL=web.js.map