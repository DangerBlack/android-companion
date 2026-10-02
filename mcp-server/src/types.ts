export type Emotion =
  | "neutral"
  | "happy"
  | "thinking"
  | "error"
  | "sleepy"
  | "listening";

export type Sound = "none" | "soft" | "alert";

export interface CompanionInstance {
  id: string;
  label: string;
  color: string;
}

export interface CompanionPayload {
  v: 1;
  instance: CompanionInstance;
  emotion: Emotion;
  text?: string;
  sound: Sound;
  ttl_ms?: number;
  ts: number;
}
