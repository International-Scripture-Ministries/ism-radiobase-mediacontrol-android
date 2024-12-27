import {WebPlugin} from '@capacitor/core';

import type {MediaControlPlugin} from './definitions';

export class MediaControlWeb extends WebPlugin implements MediaControlPlugin {

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

