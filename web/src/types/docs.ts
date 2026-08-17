import type { AuthType, ClientType, Permission, UserFieldType } from "@/types/enums";

export interface DocumentationContext {
  organisation: {
    id: number;
    name: string;
    slug: string;
    platformSlug: string;
  };
  identifiers: {
    emailRequired: boolean;
    usernameRequired: boolean;
    phoneRequired: boolean;
    emailCanLogin: boolean;
    usernameCanLogin: boolean;
    phoneCanLogin: boolean;
  };
  auth: {
    authType: AuthType;
    passwordEnabled: boolean;
    passwordMinLength: number;
    passwordMaxLength: number;
  };
  sessions: {
    accessTokenTtlSeconds: number;
    refreshTokenTtlSeconds: number;
    maxSessionsPerUser: number;
  };
  customFields: Array<{
    key: string;
    fieldType: UserFieldType;
    loginEnabled: boolean;
    required: boolean;
  }>;
  roles: Array<{
    id: number;
    name: string;
    permissions: Permission[];
    isDefault: boolean;
  }>;
  clientTypes: Array<{
    type: ClientType;
    count: number;
    requireAuthentication: boolean;
  }>;
  availablePermissions: Permission[];
}
