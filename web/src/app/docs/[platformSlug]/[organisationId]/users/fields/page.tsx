"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";

export default function FieldsPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Custom Fields</h1>
        <p className="mt-2 text-muted-foreground">
          Manage custom user fields for your organisation.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your Custom Fields</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {docs.customFields.length === 0 ? (
            <p className="text-sm text-muted-foreground">No custom fields configured</p>
          ) : (
            docs.customFields.map((field) => (
              <div key={field.key} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium font-mono">{field.key}</span>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{field.fieldType}</Badge>
                    {field.loginEnabled && (
                      <Badge variant="secondary">Login</Badge>
                    )}
                    {field.required && (
                      <Badge variant="destructive">Required</Badge>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Field Types</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <div className="grid gap-2">
            <div className="flex items-center gap-2">
              <code className="text-sm">STRING</code>
              <span className="text-sm text-muted-foreground">— Trimmed text (case-insensitive matching for login)</span>
            </div>
            <div className="flex items-center gap-2">
              <code className="text-sm">NUMBER</code>
              <span className="text-sm text-muted-foreground">— BigDecimal canonical string (1.50 == 1.5)</span>
            </div>
            <div className="flex items-center gap-2">
              <code className="text-sm">BOOLEAN</code>
              <span className="text-sm text-muted-foreground">— &quot;true&quot; or &quot;false&quot;</span>
            </div>
            <div className="flex items-center gap-2">
              <code className="text-sm">DATE</code>
              <span className="text-sm text-muted-foreground">— ISO-8601 yyyy-MM-dd</span>
            </div>
            <div className="flex items-center gap-2">
              <code className="text-sm">EMAIL</code>
              <span className="text-sm text-muted-foreground">— Trimmed + lowercased email</span>
            </div>
            <div className="flex items-center gap-2">
              <code className="text-sm">LINK</code>
              <span className="text-sm text-muted-foreground">— http(s) URL</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <Separator />

      <Endpoint
        method="GET"
        path={`/organisations/user-fields`}
        description="List all custom fields"
        auth="SERVER client or platform user with ORGANISATION_USER_FIELD_READ permission"
        response={JSON.stringify(
          docs.customFields.map((field, i) => ({
            id: i + 1,
            key: field.key,
            fieldType: field.fieldType,
            loginEnabled: field.loginEnabled,
            required: field.required,
            createdAt: "2024-01-01T00:00:00Z",
          })),
          null,
          2
        )}
      />

      <Endpoint
        method="POST"
        path={`/organisations/user-fields`}
        description="Create a custom field"
        auth="PLATFORM SUPER_USER or ORGANISATION_USER_FIELD_CREATE permission"
        request={JSON.stringify(
          {
            key: "department",
            fieldType: "STRING",
            loginEnabled: false,
            required: true,
          },
          null,
          2
        )}
      />
    </div>
  );
}
