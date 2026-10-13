export function snapToPalette(img: { width: number; height: number; rgba: Uint8Array }, palette: string[]):
  { width: number; height: number; rgba: Uint8Array; changed: number };
export function sizeProblem(kind: string, width: number, height: number): string | null;
