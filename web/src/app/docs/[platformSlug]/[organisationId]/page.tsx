"use client";

import { use } from "react";
import { useDocumentationContext } from "@/hooks/use-docs";
import { DocumentationProvider } from "@/components/docs/docs-provider";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { BookOpen, Zap, Shield, Users, Key, Settings } from "lucide-react";

export default function DocsPage({
  params,
}: {
  params: Promise<{ platformSlug: string; organisationId: string }>;
}) {
  const { platformSlug, organisationId } = use(params);
  const { data: context, isLoading, error } = useDocumentationContext(
    platformSlug,
    Number(organisationId)
  );

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-muted-foreground">Loading documentation...</div>
      </div>
    );
  }

  if (error || !context) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-destructive">Failed to load documentation context</div>
      </div>
    );
  }

  return (
    <DocumentationProvider context={context}>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {context.organisation.name} API Documentation
          </h1>
          <p className="mt-2 text-muted-foreground">
            Complete API reference and integration guide for {context.organisation.name}.
          </p>
        </div>

        <Separator />

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <BookOpen className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">Getting Started</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Learn how to integrate with the API in minutes.
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <Key className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">Authentication</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Register, login, and manage user sessions.
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <Settings className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">Client Types</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Understand WEB, SERVER, and mobile client capabilities.
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <Users className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">User Management</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Manage users, roles, and custom fields.
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <Shield className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">Token Verification</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Verify JWT tokens using public keys.
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-primary/50 transition-colors">
            <CardHeader className="pb-2">
              <Zap className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base">Quick Start</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Get up and running with your first API call.
              </p>
            </CardContent>
          </Card>
        </div>

        <Separator />

        <div>
          <h2 className="text-xl font-semibold mb-4">Your Configuration</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">Identifiers</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-1">
                  {context.identifiers.emailCanLogin && (
                    <Badge variant="secondary">Email Login</Badge>
                  )}
                  {context.identifiers.usernameCanLogin && (
                    <Badge variant="secondary">Username Login</Badge>
                  )}
                  {context.identifiers.phoneCanLogin && (
                    <Badge variant="secondary">Phone Login</Badge>
                  )}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">Custom Fields</CardTitle>
              </CardHeader>
              <CardContent>
                {context.customFields.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No custom fields configured</p>
                ) : (
                  <div className="space-y-1">
                    {context.customFields.map((field) => (
                      <Badge key={field.key} variant="outline">
                        {field.key} ({field.fieldType})
                      </Badge>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">Roles</CardTitle>
              </CardHeader>
              <CardContent>
                {context.roles.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No roles configured</p>
                ) : (
                  <div className="space-y-1">
                    {context.roles.map((role) => (
                      <Badge key={role.id} variant="outline">
                        {role.name}
                        {role.isDefault && " (default)"}
                      </Badge>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">Clients</CardTitle>
              </CardHeader>
              <CardContent>
                {context.clientTypes.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No clients configured</p>
                ) : (
                  <div className="space-y-1">
                    {context.clientTypes.map((client) => (
                      <Badge key={client.type} variant="outline">
                        {client.type} ({client.count})
                      </Badge>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>

        <Separator />

        <div>
          <h2 className="text-xl font-semibold mb-4">Base URL</h2>
          <CodeBlock
            code={`https://your-api-domain.com/${platformSlug}`}
            language="text"
          />
        </div>
      </div>
    </DocumentationProvider>
  );
}
