# Capacitor Plugin Media Control 🎵

`#capacitor-plugin-media-control`
<p>A Media player control plugin for Ionic Capacitor Android. An ability to play, pause, seek and many other basic control properties of the currently playing media.</p>

### <u>Prerequisites</u> 🛠

- Java 11+</br>
- Android API 27+</br>
- Android Studio 2020.1+</br>
- Ionic Capacitor v3+

### Feature

- [Media3](https://developer.android.com/media/media3) - Integrated with AndroidX Media3 for be.
- [ExoPlayer](https://developer.android.com/media/media3/exoplayer) - allows playback of audio files.
- [Media Session](https://developer.android.com/media/media3#session) - provides a universal way to interact with a media
  player.
- [Media3 v1.3.1](https://github.com/androidx/media/releases/tag/1.3.1) - Built using the most stable release of 

### Tested on

- Ionic Capacitor 7 <a href="https://ionicframework.com/docs" target="_blank"> Ionic Documentation</a><br/>
- Capacitor CLI 6.1.1

### Installation

```bash
npm install https://gitlab.com/apollo-apps/ism/capacitor-plugins/android/mediacontrol-android
npx cap sync
ionic capacitor build android
```

### Methods:

### play(...)

Initiates the playback of an audio track with specified options.Will overrides previous audio if already playing

#### Usage:

```typescript
player.play({
    audio: 'https://example.com/audio.mp3',
    cover: 'https://example.com/cover.jpg',
    title: 'Sample Audio',
    playbackPosition: '0',
    playbackSpeed: '1.0'
});
```

--------------------

### load(...)

Loads an audio track with the specified options and prepares it for playback.

#### Usage:

```typescript
player.load({
    audio: 'https://example.com/audio.mp3',
    cover: 'https://example.com/cover.jpg',
    title: 'Sample Audio'
}).then((result) => {
    if (result === null) {
        console.log('Audio loaded successfully');
    } else {
        console.error('Error loading audio:', result.message, result.url);
    }
}).catch((error) => {
    console.error('Unexpected error:', error);
});
```

**Returns:** `null` if the audio loads successfully without issues.</br>

--------------------

### isLoaded()

Checks if an audio track is currently loaded and ready for playback.

#### Usage:

```typescript
player.isLoaded().then((status) => {
    if (status.value) {
        console.log('Audio is loaded:', status.url);
    } else {
        console.log('No audio is loaded.');
    }
}).catch((error) => {
    console.error('Error checking audio load status:', error);
});
```

**Returns:** <code>Promise&lt;{ value: boolean; url: string; }&gt;</code>

--------------------

### playLoaded(...)

Plays the audio that has already been loaded. This method assumes that the `load` method has been called previously to
load an audio track and that `isLoaded()` has returned `true`, indicating the audio is ready for playback. If these
conditions are met, calling `playLoaded()` will start playback instantly.

#### Usage:

```typescript
await player.load({
    audio: 'https://example.com/audio.mp3',
    cover: 'https://example.com/cover.jpg',
    title: 'Sample Audio'
});

// Check if the audio is loaded
const status = await player.isLoaded();
if (status.value) {
    // Play the loaded audio    
    player.playLoaded();
} else {
    console.log('Audio is not loaded. Please load an audio track first.');
}
```

**Returns:** Throws `{Error}` If no audio is loaded or `isLoaded()` returns `false`, this method will returns void.

--------------------

### isPlaying()

Checks if an any audio track is currently running.

#### Usage:

```typescript
player.isPlaying().then((status) => {
    if (status.value) {
        console.log('Audio is playing.');
    } else {
        console.log('No audio is playing.');
    }
}).catch((error) => {
    console.error('Error checking audio playing status:', error);
});
```

**Returns:** <code>Promise&lt;{ value: boolean; }&gt;</code>

--------------------

### pause()

Pauses the currently playing audio.

#### Usage:

```typescript
player.pause();
```

--------------------

### resume()

Resumes playback of the currently paused audio.

#### Usage:

```typescript
player.resume();
```

--------------------

### stop()

Stops the currently playing audio and releases resources.

#### Usage:

```typescript
player.stop();
```

--------------------

### seek(...)

Seeks to a specific position in the currently loaded audio.

#### Usage:

```typescript
player.seek({seekTo: '15000'}); // value would be in milliseconds
```

--------------------

### speed(...)

Sets the playback speed of the currently playing audio.

#### Usage:

```typescript
player.speed({value: '1.0'})
```

--------------------

### add(...)

Adds multiple audio tracks to the playlist.

#### Usage:

```typescript
const audioTracks = [{
    audio: 'https://example.com/audio.mp3',
    cover: 'https://example.com/cover.jpg',
    title: 'Sample Audio'
},
    {
        audio: 'https://example.com/audio.mp3',
        cover: 'https://example.com/cover.jpg',
        title: 'Sample Audio'
    }];
player.add(audioTracks);
```

--------------------

### fetchPlaylist(...)

Retrieves a JSON array of all audios played in background along with following params.</br>
[Note: Once the fetchPlaylist() is called#### Usage:

#### Usage:

```typescript
player.fetchPlaylist(success => {
    console.log(success);
}, error => {
    console.log(error);
});
```

**Returns:**

```typescript
[{
    "date": "1733234351",
    "duration": "1186163",
    "position": "1185880",
    "state": "COMPLETE",
    "url": "https://example.com/audio.mp3",
    "uuid": "audio.mp3"
}, {
    "date": "1733234402",
    "duration": "1590047",
    "position": "1589628",
    "state": "COMPLETE",
    "url": "https://example.com/audio.mp3",
    "uuid": "audio.mp3"
}, {
    "date": "1733234408",
    "duration": "394475",
    "position": "202655",
    "state": "INCOMPLETE",
    "url": "audio.mp3",
    "uuid": "audio.mp3"
}]
```

--------------------

### clearPlaylist()

Clears the playlist of audios that were added using add() function.

#### Usage:

```typescript
player.clearPlaylist();
```

--------------------

### getUpdate()

Retrieves the current state and metadata of the audio player.

#### Usage:

```typescript
player.getUpdate(success => {
    console.log(success);
}, error => {
    console.log(error);
});
```

**Returns:**

```typescript
{
    Promise<{ state: string; position: string; duration: string; url: string; }>
}
```

### retrieveStoredProgress()

```typescript
retrieveStoredProgress() => Promise<{ result: JSON; }>
```

**Returns:** <code>Promise&lt;{ result: <a href="#json">JSON</a>; }&gt;</code>

--------------------


### clearStoredProgress()

```typescript
clearStoredProgress() => void
```

--------------------



--------------------

### addListener(event, ...)
Adds an event listener for player updates.</br>Whereas `playerUpdates` is the type of event which provide the realtime updates of running audio.
#### Usage:
```typescript
listener = player.addListener('playerUpdates', (data) => {  
    console.log('Received data:', data);
});
```
**Returns:**
```typescript
{
    Promise<{ state: string; position: string; duration: string; url: string; }>
}
```
