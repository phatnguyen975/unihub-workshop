import type { Identity } from "../model/access";
export interface StoragePort {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}
export type Fault =
  | "invalid"
  | "disabled"
  | "unavailable"
  | "rate"
  | "notTemporary"
  | "unknownPassword";
export class AccessError extends Error {
  kind: Fault;
  constructor(kind: Fault) {
    super(kind);
    this.kind = kind;
  }
}
export interface IdentityService {
  current(): Identity | null;
  locked(): boolean;
  storageFailed(): boolean;
  login(
    email: string,
    password: string,
    current: () => boolean,
  ): Promise<Identity | null>;
  replace(
    email: string,
    password: string,
    next: string,
    current: () => boolean,
  ): Promise<Identity | null>;
  restore(current: () => boolean): Promise<Identity | null>;
  logout(current: () => boolean): Promise<void>;
  lock(): void;
  clear(): void;
}
export type AccessState =
  | {
      kind: "anonymous";
      message?: string;
    }
  | {
      kind: "restoring" | "renewing" | "logging-out";
    }
  | {
      kind: "authenticated";
      identity: Identity;
      message?: string;
    }
  | {
      kind: "temporary";
      email: string;
      credential: string;
    }
  | {
      kind: "restore-unavailable" | "logout-unconfirmed";
    };
