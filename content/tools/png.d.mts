export function decodePng(buf: Uint8Array): { width: number; height: number; rgba: Uint8Array };
export function encodePng(width: number, height: number, rgba: Uint8Array): Uint8Array;
