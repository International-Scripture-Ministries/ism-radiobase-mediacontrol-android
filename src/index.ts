import { registerPlugin } from '@capacitor/core';

import type { MediaControlPlugin } from './definitions';

const MediaControl = registerPlugin<MediaControlPlugin>('MediaControl', {
  web: () => import('./web').then(m => new m.MediaControlWeb()),
});

export * from './definitions';
export { MediaControl };
