import { PluginListenerHandle, WebPlugin } from '@capacitor/core';
import type { MediaControlPlugin } from './definitions';
export declare class MediaControlWeb extends WebPlugin implements MediaControlPlugin {
    addListener(eventName: "playerUpdates", callback: (data: {
        state: string;
        position: string;
        duration: string;
        url: string;
    }) => void): Promise<PluginListenerHandle>;
    play(options: {
        audio: string;
        cover: string;
        title: string;
        playbackPosition: string;
        playbackSpeed: string;
    }): any;
    load(options: {
        audio: string;
        cover: string;
        title: string;
    }): any;
    isLoaded(): Promise<{
        value: boolean;
        url: string;
    }>;
    playLoaded(): void;
    isPlaying(): Promise<{
        value: boolean;
    }>;
    pause(): void;
    resume(): void;
    stop(): void;
    seek(options: {
        seekTo: string;
    }): any;
    speed(options: {
        value: string;
    }): any;
    add(audioArray: JSON[]): any;
    fetchPlaylist(options: {
        completed: string;
    }): any;
    clearPlaylist(): void;
    getDuration(options: {
        audio: string;
    }): any;
    getUpdate(): Promise<{
        state: string;
        position: string;
        duration: string;
        url: string;
    }>;
}
