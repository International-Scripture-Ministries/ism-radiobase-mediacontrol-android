import { PluginListenerHandle, WebPlugin} from '@capacitor/core';

import type {MediaControlPlugin} from './definitions';

export class MediaControlWeb extends WebPlugin implements MediaControlPlugin {

    addListener(
        eventName: "playerUpdates",
        callback: (data: { state: string; position: string; duration: string; url: string }) => void
      ): Promise<PluginListenerHandle> {
        // Validate eventName
        if (eventName !== "playerUpdates") {
          throw new Error(`Unsupported event: ${eventName}`);
        }
    
        // Example: Set up a listener for media player updates
        const listener = (event: any) => {
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


    play(options: { audio: string; cover: string; title: string; playbackPosition: string; playbackSpeed: string; }): any {
        return options
    }

    load(options: { audio: string; cover: string; title: string; }): any {
        return Promise.resolve(options);
    }

    isLoaded(): Promise<{ value: boolean, url: string; }> {
        return Promise.resolve({value: false, url: ""});
    }

    playLoaded(): void {
        return
    }

    isPlaying(): Promise<{ value: boolean }> {
        return Promise.resolve({value: false});
    }

    pause(): void {
        return
    }

    resume(): void {
        return
    }

    stop(): void {
        return
    }

    seek(options: { seekTo: string }): any {
        return options
    }

    speed(options: { value: string }): any {
        return options
    }

    add(audioArray: JSON[]): any {
        return audioArray
    }

    fetchPlaylist(options: { completed: string; }): any {
        return options
    }

    clearPlaylist(): void {
        return
    }

    getDuration(options: { audio: string; }): any {
        return Promise.resolve(options);
    }

    getUpdate(): Promise<{ state: string; position: string; duration: string; url: string }> {
        throw new Error('Method not implemented.');
    }

}

