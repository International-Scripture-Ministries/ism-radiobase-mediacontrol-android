var capacitorMediaPlayer = (function (exports, core) {
    'use strict';

    const MediaControl = core.registerPlugin('MediaControl', {
        web: () => Promise.resolve().then(function () { return web; }).then(m => new m.MediaControlWeb()),
    });

    class MediaControlWeb extends core.WebPlugin {
        addListener(eventName, callback) {
            // Validate eventName
            if (eventName !== "playerUpdates") {
                throw new Error(`Unsupported event: ${eventName}`);
            }
            // Example: Set up a listener for media player updates
            const listener = (event) => {
                // Construct the expected data structure
                const data = {
                    state: event.state || "unknown", // Adjust based on your event source
                    position: event.position || "0",
                    duration: event.duration || "0",
                    url: event.url || "",
                };
                callback(data);
            };
            // Example: Attach to a web event (e.g., media session or custom event)
            // Replace with actual event source (e.g., navigator.mediaSession)
            window.addEventListener("playerUpdates", listener); // Adjust event name/source
            return Promise.resolve({
                remove: async () => {
                    window.removeEventListener("playerUpdates", listener); // Adjust event name
                },
            });
        }
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
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        MediaControlWeb: MediaControlWeb
    });

    exports.MediaControl = MediaControl;

    Object.defineProperty(exports, '__esModule', { value: true });

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
