import type { PluginListenerHandle } from '@capacitor/core';
export interface MediaControlPlugin {
    /**
     * Initiates the playback of an audio track with specified options.
     * Will overrides previous audio if already playing
     *
     * @param {string} options.audio - The URL or path to the audio file to be played.
     * @param {string} options.cover - The URL or path to the cover image associated with the audio track.
     * @param {string} options.title - The title of the audio track.
     * @param {string} options.playbackPosition - The starting playback position in the audio file, typically in milliseconds (e.g., "1000" for a second).
     * @param {string} options.playbackSpeed - The speed at which the audio should be played, where 1.0 is normal speed.
     *
     * @returns {void} This method does not return any value.
     *
     * @example
     * player.play({
     *     audio: 'https://example.com/audio.mp3',
     *     cover: 'https://example.com/cover.jpg',
     *     title: 'Sample Audio',
     *     playbackPosition: '0',
     *     playbackSpeed: '1.0'
     * });
     */
    play(options: {
        audio: string;
        cover: string;
        title: string;
        playbackPosition: string;
        playbackSpeed: string;
    }): void;
    /**
     * Loads an audio track with the specified options and prepares it for playback.
     *
     * @param {string} options.audio - The URL or path to the audio file to be loaded.
     * @param {string} options.cover - The URL or path to the cover image associated with the audio track.
     * @param {string} options.title - The title of the audio track.
     *
     * @example
     * player.load({
     *     audio: 'https://example.com/audio.mp3',
     *     cover: 'https://example.com/cover.jpg',
     *     title: 'Sample Audio'
     * }).then((result) => {
     *     if (result === null) {
     *         console.log('Audio loaded successfully');
     *     } else {
     *         console.error('Error loading audio:', result.message, result.url);
     *     }
     * }).catch((error) => {
     *     console.error('Unexpected error:', error);
     * });
     *
     * - returns `null` if the audio loads successfully without issues.
     * - returns a json body if an error occurs:
     *   ```json
     *   {
     *       "message": "Audio Error",
     *       "url": "https://example.com/audio.mp3"
     *   }
     *   ````
     */
    load(options: {
        audio: string;
        cover: string;
        title: string;
    }): Promise<any>;
    /**
     * Checks if an audio track is currently loaded and ready for playback.
     *
     * @returns {Promise<{ value: boolean; url: string; }>}
     * - `value`: A boolean indicating whether the audio is loaded (`true`) or not (`false`).
     * - `url`: The audio file that currently being loaded.
     *
     * @example
     * player.isLoaded().then((status) => {
     *     if (status.value) {
     *         console.log('Audio is loaded:', status.url);
     *     } else {
     *         console.log('No audio is loaded.');
     *     }
     * }).catch((error) => {
     *     console.error('Error checking audio load status:', error);
     * });
     */
    isLoaded(): Promise<{
        value: boolean;
        url: string;
    }>;
    /**
     * Plays the audio that has already been loaded.
     *
     * This method assumes that the `load` method has been called previously to load an audio track
     * and that `isLoaded()` has returned `true`, indicating the audio is ready for playback.
     *
     * If these conditions are met, calling `playLoaded()` will start playback instantly.
     *
     * @throws {Error} If no audio is loaded or `isLoaded()` returns `false`, this method will returns void.
     *
     * @example
     * // Load an audio track
     * await player.load({
     *     audio: 'https://example.com/audio.mp3',
     *     cover: 'https://example.com/cover.jpg',
     *     title: 'Sample Audio'
     * });
     *
     * // Check if the audio is loaded
     * const status = await player.isLoaded();
     * if (status.value) {
     *     // Play the loaded audio
     *     player.playLoaded();
     * } else {
     *     console.log('Audio is not loaded. Please load an audio track first.');
     * }
     */
    playLoaded(): void;
    /**
     * Checks if an any audio track is currently running.
     *
     * @returns {Promise<{ value: boolean; }>}
     * - `value`: A boolean indicating whether the audio is running (`true`) or not (`false`).
     *
     * @example
     * player.isPlaying().then((status) => {
     *     if (status.value) {
     *         console.log('Audio is playing.');
     *     } else {
     *         console.log('No audio is playing.');
     *     }
     * }).catch((error) => {
     *     console.error('Error checking audio playing status:', error);
     * });
     */
    isPlaying(): Promise<{
        value: boolean;
    }>;
    /**
     * Pauses the currently playing audio.
     *
     * @returns {void}
     */
    pause(): void;
    /**
     * Resumes playback of the currently paused audio.
     *
     * @returns {void}
     */
    resume(): void;
    /**
     * Stops the currently playing audio and releases resources.
     *
     * @returns {void}
     */
    stop(): void;
    /**
     * Seeks to a specific position in the currently loaded audio.
     *
     * @param {string} options.seekTo - The position to seek to, typically in milliseconds or a time string (e.g., "1000" for one second).
     *
     * @returns {void}
     */
    seek(options: {
        seekTo: string;
    }): void;
    /**
     * Sets the playback speed of the currently playing audio.
     *
     * @param {string} options.value - The desired playback speed (e.g., "1.0" for normal speed, "1.5" for 1.5x speed).
     *
     * @returns {void}
     */
    speed(options: {
        value: string;
    }): void;
    /**
     * Adds multiple audio tracks to the playlist.
     *
     * @param {JSON[]} audioArray - An array of audio track objects, each containing:
     * - `audio`: The URL or path of the audio file.
     * - `cover`: The URL or path of the cover image associated with the audio.
     * - `title`: The title of the audio track.
     *
     * @returns {void}
     *
     * @example
     * const audioTracks = [
     *     {
     *          audio: 'https://example.com/audio.mp3',
     *          cover: 'https://example.com/cover.jpg',
     *          title: 'Sample Audio'
     *     },
     *     {
     *          audio: 'https://example.com/audio.mp3',
     *          cover: 'https://example.com/cover.jpg',
     *          title: 'Sample Audio'
     *     }
     * ];
     * player.add(audioTracks);
     */
    add(audioArray: JSON[]): void;
    /**
     * Retrieves a JSON array of all audios played in background along with following params.
     * [Note: Once the fetchPlaylist() is called
     *
     * @param {string} options.completed - String type of true/false which either send entire list in result or just the last INCOMPLETE audio.
     *
     * @example
     * player.fetchPlaylist({ completed: 'true' },
     *                      success => {
     *                         console.log(success);
     *                     }, error => {
     *                         console.log(error);
     *                     });
     * returns:  [{"date":"1733234351","duration":"1186163","position":"1185880","state":"COMPLETE","url":"https://example.com/audio.mp3","uuid":"audio.mp3"},{"date":"1733234402","duration":"1590047","position":"1589628","state":"COMPLETE","url":"https://example.com/audio.mp3","uuid":"audio.mp3"},{"date":"1733234408","duration":"394475","position":"202655","state":"INCOMPLETE","url":"audio.mp3","uuid":"audio.mp3"}]
     *
     * @param {String} `date`: EPOCH date/time of audio which is completely listened.
     * @param {String} `duration`: The duration of an audio which is listened.
     * @param {String} `position`: The current position of an audio which is listened.
     * @param {String} `state`: The state of an audio which is listened completed or not  (e.g. "COMPLETE", "INCOMPLETE").
     * @param {String} `url`: The URL of an audio.
     */
    fetchPlaylist(options: {
        completed: string;
    }): Promise<{
        result: JSON;
    }>;
    getDuration(options: {
        audio: string;
    }): Promise<{
        url: string;
        duration: boolean;
    }>;
    /**
     * Clears the playlist of audios that were added using add() function.
     *
     * @returns {void}
     */
    clearPlaylist(): void;
    /**
     * Retrieves the current state and metadata of the audio player.
     *
     * @returns {Promise<{ state: string; position: string; duration: string; url: string; }>}
     * - `state`: The current state of the player (e.g. "null", "PLAYING", "PAUSE", "END").
     * - `position`: The current playback position, typically in milliseconds string.
     * - `duration`: The total duration of the loaded audio, typically in milliseconds string.
     * - `url`: The URL of the currently loaded audio.
     */
    getUpdate(): Promise<{
        state: string;
        position: string;
        duration: string;
        url: string;
    }>;
    /**
     * Returns duration of the provided audio in milliseconds.
     *
     * @returns {Promise<{ url: boolean; duration: string; }>}
     * - `url`: The URL of the currently checked audio.
     * - `duration`: The total duration of the checked audio.
     *
     * @example
     * player.getDuration({
     *     audio: 'https://example.com/audio.mp3'
     * }).then((result) => {
     *     console.log('Audio url:', result.url);
     *     console.log('Audio duration:', result.duration);
     * }).catch((error) => {
     *     console.error('Error checking audio duration:', error);
     * });
     */
    getDuration(options: {
        audio: string;
    }): Promise<{
        url: string;
        duration: boolean;
    }>;
    retrieveStoredProgress(): Promise<{
        result: JSON;
    }>;
    clearStoredProgress(): void;
    /**
     * Language and translated labels for the Android Auto library. Optional:
     * without it the car uses the station's default language and the labels
     * bundled with the web build. Android only.
     *
     */
    setAutoConfig(options: AutoConfig): Promise<void>;
    /**
     * Audio the app finished downloading. Android Auto lists it under
     * Downloads, plays it with no network, and the player uses the local file
     * instead of the stream for the same URL. Send the full list on launch and
     * whenever downloads change; each call replaces the previous list.
     * Android only.
     */
    setAutoDownloads(options: {
        items: AutoDownload[];
    }): Promise<void>;
    /**
     * What the service is playing when Android Auto started it. Resolves to an
     * empty object when nothing is playing or playback came from the app.
     * Android only.
     */
    getNowPlaying(): Promise<Partial<AutoNowPlaying>>;
    /**
     * Adds an event listener for player updates.
     *
     * @param {string} eventName - The name of the event to listen for (e.g., `'playerUpdates'`).
     * @param {Function} listenerFunc - The callback function invoked when the event is triggered.
     * The callback receives an object containing:
     * - `state`: The current state of the player (e.g., "null", "PLAYING", "PAUSE", "END").
     * - `position`: The current playback position, typically in milliseconds string.
     * - `duration`: The total duration of the loaded audio in milliseconds string.
     * - `url`: The URL of the currently loaded audio.
     *
     * @returns {PluginListenerHandle} A handle to manage the listener.
     *
     * @example
     * player.addListener('playerUpdates', (data) => {
     *   console.log('Received data:', data);
     * });
     */
    addListener(eventName: "playerUpdates", listenerFunc: (data: {
        state: string;
        position: string;
        duration: string;
        url: string;
    }) => void): Promise<PluginListenerHandle>;
    /**
     * Fires when Android Auto starts, changes or pauses a track. Android only.
     */
    addListener(eventName: "nowPlaying", listenerFunc: (data: AutoNowPlaying) => void): Promise<PluginListenerHandle>;
}
export interface AutoNowPlaying {
    source: 'android_auto';
    /** Audio URL, the same value `playerUpdates` reports as `url`. */
    url: string;
    title: string;
    subtitle: string;
    artwork: string;
    state: 'PLAYING' | 'PAUSE';
    /** Milliseconds, as strings like in `playerUpdates`. */
    position: string;
    duration: string;
    kind: 'bible' | 'study' | 'unknown';
    /** True when the track plays from a downloaded file. */
    downloaded: boolean;
    bookId?: string;
    /** Bible chapter ID, e.g. "LUK.3". */
    chapterId?: string;
    /** CMP teaching UUID. */
    studyId?: string;
}
export interface AutoConfig {
    /** Station language `code`, e.g. "eng". */
    languageCode?: string;
    /** Keys: bible, study, old_testament, new_testament, guidelines, downloads, offline, unavailable. */
    labels?: Record<string, string>;
}
export interface AutoDownload {
    kind: 'bible' | 'study';
    /** Bible chapter ID ("LUK.3") or CMP teaching UUID. */
    id: string;
    bookId: string;
    bookName: string;
    /** Sorts books in the Downloads list. */
    bookOrder: number;
    /** Chapter number, or the study's position inside its book. */
    order: number;
    title: string;
    /** The streaming URL the app would otherwise play. */
    remoteUrl: string;
    /** Absolute path or file:// URI of the downloaded file. */
    localPath: string;
    artUrl?: string;
    durationSeconds?: number;
}
