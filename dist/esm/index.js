import { registerPlugin } from '@capacitor/core';
const MediaControl = registerPlugin('MediaControl', {
    web: () => import('./web').then(m => new m.MediaControlWeb()),
});
export * from './definitions';
export { MediaControl };
//# sourceMappingURL=index.js.map