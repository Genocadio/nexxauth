"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CodeBlock } from "./code-block";

interface EndpointProps {
  method: "GET" | "POST" | "PATCH" | "DELETE";
  path: string;
  description: string;
  auth?: string;
  request?: string;
  response?: string;
}

const METHOD_COLORS = {
  GET: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300",
  POST: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300",
  PATCH: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300",
  DELETE: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300",
};

export function Endpoint({ method, path, description, auth, request, response }: EndpointProps) {
  return (
    <Card className="mb-6">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-3">
          <Badge variant="secondary" className={METHOD_COLORS[method]}>
            {method}
          </Badge>
          <code className="text-sm font-mono">{path}</code>
        </div>
        <CardTitle className="text-base mt-2">{description}</CardTitle>
        {auth && (
          <p className="text-sm text-muted-foreground">
            <span className="font-medium">Auth:</span> {auth}
          </p>
        )}
      </CardHeader>
      <CardContent className="space-y-4">
        {request && (
          <div>
            <h4 className="mb-2 text-sm font-medium">Request Body</h4>
            <CodeBlock code={request} language="json" />
          </div>
        )}
        {response && (
          <div>
            <h4 className="mb-2 text-sm font-medium">Response</h4>
            <CodeBlock code={response} language="json" />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
