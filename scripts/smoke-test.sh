#!/usr/bin/env bash
# End-to-end smoke test of the nexxauth API with curl.
#
# Usage:
#   BASE=http://localhost:8080 MGMT=http://localhost:8081 scripts/smoke-test.sh
#
# Expects a fresh app instance with at least:
#   --app.rate-limit.login.capacity=5 --app.rate-limit.register.capacity=5
# (rate limits are per-IP token buckets, so run against a freshly started server)

set -u
BASE="${BASE:-http://localhost:8080}"
MGMT="${MGMT:-http://localhost:8081}"
LOG="${LOG:-}"  # optional app log file to check audit events against
RESP=/tmp/nexxauth-resp.json
HDRS=/tmp/nexxauth-headers.txt
PASS=0
FAIL=0

req() { # method path [curl args...]
  local method="$1" path="$2"
  shift 2
  curl -s -o "$RESP" -D "$HDRS" -X "$method" "$BASE$path" "$@"
}

status() { awk 'NR==1{print $2}' "$HDRS"; }

field() { python3 -c "import json; d=json.load(open('$RESP')); print(d$1)" 2>/dev/null || echo "__MISSING__"; }

jlen() { python3 -c "import json; print(len(json.load(open('$RESP'))))" 2>/dev/null || echo "__MISSING__"; }

nlen() { python3 -c "import json; d=json.load(open('$RESP')); print(len(d$1))" 2>/dev/null || echo "__MISSING__"; }

header() { tr -d '\r' < "$HDRS" | awk -v h="$1" 'tolower($1)==tolower(h)":" {print substr($0, index($0, ":")+2); exit}'; }

check() { # label expected actual
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "  PASS  $1 -> $3"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL  $1 expected=$2 actual=$3"
  fi
}

echo "== register (creates platform + super user) =="
req POST /auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@nexx.io","password":"sup3r-secret","phone":"+123","platformName":"Analytical Engines"}'
check "register" 201 "$(status)"
BA=$(field '["accessToken"]'); BR=$(field '["refreshToken"]')
check "token type" Bearer "$(field '["tokenType"]')"
check "role" SUPER_USER "$(field '["user"]["role"]')"
check "slug derived" analytical-engines "$(field '["user"]["platform"]["slug"]')"

echo "== me / profile / platform =="
req GET /auth/me -H "Authorization: Bearer $BA"
check "me" 200 "$(status)"
check "me email" ada@nexx.io "$(field '["email"]')"
req PATCH /auth/me -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' -d '{"phone":"+999"}'
check "patch me" 200 "$(status)"
check "patch me phone" +999 "$(field '["phone"]')"
req GET /analytical-engines -H "Authorization: Bearer $BA"
check "get platform" 200 "$(status)"
check "user count" 1 "$(field '["userCount"]')"

echo "== member lifecycle =="
req POST /analytical-engines/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Grace","lastName":"Hopper","email":"grace@nexx.io","password":"readonly-pw"}'
check "add member" 201 "$(status)"
check "member role default" READ_ONLY "$(field '["role"]')"
MID=$(field '["id"]')

req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"grace@nexx.io","password":"readonly-pw"}'
check "member login" 200 "$(status)"
MA=$(field '["accessToken"]')

req GET /analytical-engines/users -H "Authorization: Bearer $MA"
check "member lists users" 200 "$(status)"
check "member sees 2 users" 2 "$(jlen)"

req POST /analytical-engines/users -H "Authorization: Bearer $MA" -H 'Content-Type: application/json' \
  -d '{"firstName":"X","lastName":"Y","email":"x@nexx.io","password":"password1"}'
check "read-only cannot add" 403 "$(status)"

echo "== organisations (platform-scoped CRUD) =="
req POST /analytical-engines/organisations -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Nexx Labs","description":"R&D"}'
check "create org (auto slug)" 201 "$(status)"
check "org slug derived" nexx-labs "$(field '["slug"]')"
req POST /analytical-engines/organisations -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Nexx Services","slug":"services"}'
check "create org (explicit slug)" 201 "$(status)"
req GET /analytical-engines/organisations -H "Authorization: Bearer $MA"
check "read-only lists orgs" 200 "$(status)"
check "two orgs" 2 "$(jlen)"
req POST /analytical-engines/organisations -H "Authorization: Bearer $MA" -H 'Content-Type: application/json' \
  -d '{"name":"Nope"}'
check "read-only cannot create org" 403 "$(status)"
req POST /analytical-engines/organisations -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Dup","slug":"services"}'
check "duplicate org slug" 409 "$(status)"
req GET /analytical-engines/organisations/nexx-labs -H "Authorization: Bearer $BA"
check "get org" 200 "$(status)"
req PATCH /analytical-engines/organisations/nexx-labs -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Nexx Labs HQ","slug":"hq"}'
check "update org" 200 "$(status)"
check "org renamed slug" hq "$(field '["slug"]')"
req DELETE /analytical-engines/organisations/hq -H "Authorization: Bearer $BA"
check "delete org" 204 "$(status)"
req GET /analytical-engines/organisations/hq -H "Authorization: Bearer $BA"
check "deleted org 404" 404 "$(status)"

echo "== organisation RBAC (roles + org users, no org auth) =="
req POST /analytical-engines/organisations -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Rbac Corp","slug":"rbac-corp"}'
check "create rbac org" 201 "$(status)"
req POST /analytical-engines/organisations/rbac-corp/roles -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Admin","permissions":["ORGANISATION_USER_READ","ORGANISATION_USER_CREATE"]}'
check "create role with permissions" 201 "$(status)"
check "role permissions count" 2 "$(nlen '["permissions"]')"
RID=$(field '["id"]')
req POST /analytical-engines/organisations/rbac-corp/roles -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Viewer"}'
check "create role without permissions" 201 "$(status)"
check "role zero permissions" 0 "$(nlen '["permissions"]')"
req GET /analytical-engines/organisations/rbac-corp/roles -H "Authorization: Bearer $BA"
check "list roles" 200 "$(status)"
check "two roles" 2 "$(jlen)"
req POST /analytical-engines/organisations/rbac-corp/roles -H "Authorization: Bearer $MA" -H 'Content-Type: application/json' \
  -d '{"name":"Nope"}'
check "read-only cannot create role" 403 "$(status)"
req POST /analytical-engines/organisations/rbac-corp/roles -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Admin"}'
check "duplicate role name" 409 "$(status)"
req POST /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Org\",\"lastName\":\"User\",\"username\":\"orguser\",\"email\":\"org@acme.io\",\"roleIds\":[$RID]}"
check "create org user" 201 "$(status)"
check "org user username" orguser "$(field '["username"]')"
check "org user role" Admin "$(field '["roles"][0]["name"]')"
OUID=$(field '["id"]')
req POST /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"No","lastName":"Id"}'
check "org user without identifiers" 201 "$(status)"
req POST /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"D","lastName":"Up","username":"orguser"}'
check "duplicate org username" 409 "$(status)"
req GET /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $MA"
check "read-only lists org users" 200 "$(status)"
req POST /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $MA" -H 'Content-Type: application/json' \
  -d '{"firstName":"X","lastName":"Y"}'
check "read-only cannot create org user" 403 "$(status)"
req PATCH /analytical-engines/organisations/rbac-corp -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"useEmailAsUsername":true}'
check "enable email-as-username" 200 "$(status)"
check "setting reflected" True "$(field '["useEmailAsUsername"]')"
req POST /analytical-engines/organisations/rbac-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"No","lastName":"Email"}'
check "email required when setting on" 400 "$(status)"
req PATCH /analytical-engines/organisations/rbac-corp/users/$OUID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"email":""}'
check "clearing email rejected when setting on" 400 "$(status)"
req PATCH /analytical-engines/organisations/rbac-corp/users/$OUID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Renamed","roleIds":[]}'
check "update org user" 200 "$(status)"
check "org user renamed" Renamed "$(field '["firstName"]')"
check "org user roles cleared" 0 "$(nlen '["roles"]')"
req DELETE /analytical-engines/organisations/rbac-corp/users/$OUID -H "Authorization: Bearer $BA"
check "delete org user" 204 "$(status)"
req GET /analytical-engines/organisations/rbac-corp/users/$OUID -H "Authorization: Bearer $BA"
check "deleted org user 404" 404 "$(status)"
req DELETE /analytical-engines/organisations/rbac-corp/roles/$RID -H "Authorization: Bearer $BA"
check "delete role" 204 "$(status)"
req GET /analytical-engines/organisations/rbac-corp/roles/$RID -H "Authorization: Bearer $BA"
check "deleted role 404" 404 "$(status)"

echo "== organisation auth (org users log in under the platform slug) =="
req POST /analytical-engines/organisations -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Auth Corp","slug":"auth-corp"}'
check "create auth org" 201 "$(status)"
AORGID=$(field '["id"]')

# public verification keys are fetchable without auth
req GET /analytical-engines/organisations/auth-corp/keys
check "public keys (no auth)" 200 "$(status)"
check "one active key" 1 "$(jlen)"
check "key is active" True "$(field '[0]["active"]')"

# org register returns org tokens (signed by the org's own RSA key)
req POST /analytical-engines/auth/register -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"username\":\"bob\",\"password\":\"orgpass1\",\"firstName\":\"Bob\",\"lastName\":\"Builder\"}"
check "org register" 201 "$(status)"
OBA=$(field '["accessToken"]')
check "org token type" Bearer "$(field '["tokenType"]')"
check "org user role count" 0 "$(nlen '["user"]["roles"]')"

# org login
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"bob\",\"password\":\"orgpass1\"}"
check "org login" 200 "$(status)"
OBA2=$(field '["accessToken"]'); OBR=$(field '["refreshToken"]')
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"bob\",\"password\":\"wrong\"}"
check "org login wrong password" 401 "$(status)"

# a client identifies the organisation: login/register need no organisationId
req POST /analytical-engines/organisations/auth-corp/clients -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Smoke App","type":"WEB"}'
check "create client" 201 "$(status)"
CLIKEY=$(field '["clientKey"]')
req POST /analytical-engines/auth/login -H "X-Client-Id: $CLIKEY" -H 'Content-Type: application/json' \
  -d '{"identifier":"bob","authType":"PASSWORD","password":"orgpass1"}'
check "org login via client (no org id)" 200 "$(status)"
req POST /analytical-engines/auth/register -H "X-Client-Id: $CLIKEY" -H 'Content-Type: application/json' \
  -d '{"username":"cliuser","password":"orgpass1","firstName":"Cli","lastName":"User"}'
check "org register via client (no org id)" 201 "$(status)"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier":"cliuser","password":"orgpass1"}'
check "org login without client and without org id" 400 "$(status)"

# org users can read themselves by default
req GET /analytical-engines/organisations/auth-corp/users/me -H "Authorization: Bearer $OBA2"
check "org users/me" 200 "$(status)"
check "org me identifier" bob "$(field '["username"]')"

# org token has no platform power
req GET /auth/me -H "Authorization: Bearer $OBA2"
check "org token not a platform token" 401 "$(status)"
req GET /analytical-engines -H "Authorization: Bearer $OBA2"
check "org token cannot read platform" 401 "$(status)"

# org users without permissions cannot read the org's user list
req GET /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $OBA2"
check "org user w/o permission cannot list" 403 "$(status)"

# give bob a role with the read permission, then the list works
req POST /analytical-engines/organisations/auth-corp/roles -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Readers","permissions":["ORGANISATION_USER_READ"]}'
check "create reader role" 201 "$(status)"
BROLE=$(field '["id"]')
req GET /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $BA"
check "list org users as platform super" 200 "$(status)"
BOBID=$(python3 -c "import json; d=json.load(open('$RESP')); print([u['id'] for u in d if u['username']=='bob'][0])")
req PATCH /analytical-engines/organisations/auth-corp/users/$BOBID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d "{\"roleIds\":[$BROLE]}"
check "assign read role" 200 "$(status)"
req GET /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $OBA2"
check "org user with permission lists" 200 "$(status)"
req POST /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $OBA2" -H 'Content-Type: application/json' \
  -d '{"firstName":"Nope","lastName":"Nope","username":"nope"}'
check "org user w/o create permission" 403 "$(status)"

# every org user reads their own org context by default (no permission needed)
req GET /analytical-engines/organisations/auth-corp -H "Authorization: Bearer $OBA2"
check "org user reads own org" 200 "$(status)"
check "own org slug" auth-corp "$(field '["slug"]')"

# ...but the platform's org directory is off-limits to org users
req GET /analytical-engines/organisations -H "Authorization: Bearer $OBA2"
check "org user cannot list org directory" 403 "$(status)"

# give bob UPDATE permission: he can edit others, but not delete (no DELETE)
req POST /analytical-engines/organisations/auth-corp/roles -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"name":"Editors","permissions":["ORGANISATION_USER_UPDATE"]}'
check "create editor role" 201 "$(status)"
EROLE=$(field '["id"]')
req POST /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Eve","lastName":"Target","username":"eve"}'
check "create target user" 201 "$(status)"
EVEID=$(field '["id"]')
req PATCH /analytical-engines/organisations/auth-corp/users/$BOBID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d "{\"roleIds\":[$BROLE,$EROLE]}"
check "assign read+update roles" 200 "$(status)"
req PATCH /analytical-engines/organisations/auth-corp/users/$EVEID -H "Authorization: Bearer $OBA2" -H 'Content-Type: application/json' \
  -d '{"firstName":"Eve Edited"}'
check "org user with UPDATE edits another" 200 "$(status)"
check "edited name" "Eve Edited" "$(field '["firstName"]')"
req DELETE /analytical-engines/organisations/auth-corp/users/$EVEID -H "Authorization: Bearer $OBA2"
check "org user w/o DELETE permission" 403 "$(status)"

echo "== org auth config (password rules, org-level only) =="
req GET /analytical-engines/organisations/auth-corp/auth-config -H "Authorization: Bearer $BA"
check "get auth config" 200 "$(status)"
check "default auth type" PASSWORD "$(field '["authType"]')"
check "default min length" 8 "$(field '["passwordMinLength"]')"
check "default max length" 72 "$(field '["passwordMaxLength"]')"
check "default no expiry" 0 "$(field '["passwordExpirationDays"]')"
check "default no history" 0 "$(field '["passwordHistoryCount"]')"

# org users of the org can read the config too; platform-only list is untouched
req GET /analytical-engines/organisations/auth-corp/auth-config -H "Authorization: Bearer $OBA2"
check "org user reads auth config" 200 "$(status)"

# tighten min length to 12; register with an 8-char password now fails
req PATCH /analytical-engines/organisations/auth-corp/auth-config -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"passwordMinLength":12}'
check "update auth config" 200 "$(status)"
check "min length updated" 12 "$(field '["passwordMinLength"]')"
req POST /analytical-engines/auth/register -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"username\":\"shortpw\",\"password\":\"shortpass\",\"firstName\":\"S\",\"lastName\":\"P\"}"
check "org register rejects too-short password" 400 "$(status)"

# create user without password: no auth, cannot login; then set one via PATCH
req POST /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Pam","lastName":"NoAuth","username":"pam"}'
check "create user without password" 201 "$(status)"
PAMID=$(field '["id"]')
check "no auth type yet" None "$(field '["authType"]')"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"pam\",\"password\":\"whatever1\"}"
check "user without auth cannot login" 401 "$(status)"
req PATCH /analytical-engines/organisations/auth-corp/users/$PAMID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d "{\"password\":\"longenoughpass\"}"
check "set password via patch" 200 "$(status)"
check "auth type set" PASSWORD "$(field '["authType"]')"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"pam\",\"password\":\"longenoughpass\"}"
check "user can login after password set" 200 "$(status)"

# history: enable keep-2, change password, reuse of the original is rejected
req PATCH /analytical-engines/organisations/auth-corp/auth-config -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"passwordMinLength":8,"passwordHistoryCount":2}'
check "enable password history" 200 "$(status)"
req PATCH /analytical-engines/organisations/auth-corp/users/$PAMID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"password":"secondpass"}'
check "change to second password" 200 "$(status)"
req PATCH /analytical-engines/organisations/auth-corp/users/$PAMID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"password":"longenoughpass"}'
check "reuse of old password rejected" 400 "$(status)"

echo "== org user actions (temporary password + required fields) =="
# a temporary password (set by the platform user) gates the session until changed
req POST /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Tina","lastName":"Temp","username":"tina","password":"temp-pass1","temporaryPassword":true}'
check "create user with temporary password" 201 "$(status)"
check "temporary flag echoed" True "$(field '["temporaryPassword"]')"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"tina\",\"password\":\"temp-pass1\"}"
check "temporary login gated" 200 "$(status)"
check "change-password action returned" CHANGE_PASSWORD "$(field '["actions"][0]')"
check "no refresh token while action pending" None "$(field '["refreshToken"]')"
check "access ttl fixed at 5 min" 300 "$(field '["expiresInSeconds"]')"
TAT=$(field '["accessToken"]')
req GET /analytical-engines/organisations/auth-corp/users/me -H "Authorization: Bearer $TAT"
check "other endpoints closed while action pending" 401 "$(status)"
req POST /analytical-engines/organisations/auth-corp/users/me/change-password -H "Authorization: Bearer $TAT" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"temp-pass1","newPassword":"brand-new-pass"}'
check "change password completes the action" 204 "$(status)"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"tina\",\"password\":\"brand-new-pass\"}"
check "tina logs in after the change" 200 "$(status)"
check "no actions after the change" 0 "$(nlen '["actions"]')"
check "normal access ttl restored" 900 "$(field '["expiresInSeconds"]')"

# a required user field surfaces the advisory UPDATE_PROFILE action
req POST /analytical-engines/organisations/auth-corp/user-fields -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"key":"department","label":"Department","fieldType":"STRING","loginEnabled":false,"required":true}'
check "create required field" 201 "$(status)"
check "required flag echoed" True "$(field '["required"]')"
req POST /analytical-engines/organisations/auth-corp/users -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"firstName":"Uma","lastName":"Incomplete","username":"uma","password":"longpass1"}'
check "create user without required field" 201 "$(status)"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"uma\",\"password\":\"longpass1\"}"
check "update-profile action returned" UPDATE_PROFILE "$(field '["actions"][0]')"
check "non-gating: normal access ttl" 900 "$(field '["expiresInSeconds"]')"
UMA=$(field '["accessToken"]')
req GET /analytical-engines/organisations/auth-corp/users/me -H "Authorization: Bearer $UMA"
check "non-gating: profile reachable" 200 "$(status)"
req PATCH /analytical-engines/organisations/auth-corp/users/me -H "Authorization: Bearer $UMA" -H 'Content-Type: application/json' \
  -d '{"metadata":{"department":"engineering"}}'
check "complete the update-profile action" 200 "$(status)"
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"uma\",\"password\":\"longpass1\"}"
check "no actions after completing profile" 0 "$(nlen '["actions"]')"

# refresh rotation + reuse detection on org tokens
req POST /analytical-engines/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OBR\"}"
check "org refresh rotates" 200 "$(status)"
OBR2=$(field '["refreshToken"]')
req POST /analytical-engines/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OBR\"}"
check "org reused token rejected" 401 "$(status)"
req POST /analytical-engines/auth/logout -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OBR2\"}"
check "org logout" 204 "$(status)"

echo "== org session settings (token TTLs + concurrent session limit) =="
req GET /analytical-engines/organisations/auth-corp/session-settings -H "Authorization: Bearer $BA"
check "session settings defaults" 200 "$(status)"
check "default access ttl" 900 "$(field '["accessTokenTtlSeconds"]')"
check "default refresh ttl" 604800 "$(field '["refreshTokenTtlSeconds"]')"
check "default max sessions" 5 "$(field '["maxSessionsPerUser"]')"

# tighten: 2-min access tokens, 1-hour refresh tokens, one session per user
req PATCH /analytical-engines/organisations/auth-corp/session-settings -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"accessTokenTtlSeconds":120,"refreshTokenTtlSeconds":3600,"maxSessionsPerUser":1}'
check "update session settings" 200 "$(status)"
check "access ttl applied" 120 "$(field '["accessTokenTtlSeconds"]')"

# the register/login response reports the org-specific access-token lifetime
req POST /analytical-engines/auth/register -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"username\":\"carol\",\"password\":\"orgpass1\",\"firstName\":\"Carol\",\"lastName\":\"C\"}"
check "register carol" 201 "$(status)"
check "carol access ttl reported" 120 "$(field '["expiresInSeconds"]')"
CBR=$(field '["refreshToken"]')

# max sessions = 1: the next login evicts the previous session, quietly
req POST /analytical-engines/auth/login -H 'Content-Type: application/json' \
  -d "{\"organisationId\":$AORGID,\"identifier\":\"carol\",\"password\":\"orgpass1\"}"
check "carol second session" 200 "$(status)"
CBR2=$(field '["refreshToken"]')
req POST /analytical-engines/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$CBR\"}"
check "evicted session refresh rejected" 401 "$(status)"
req POST /analytical-engines/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$CBR2\"}"
check "newest session survives eviction (not theft)" 200 "$(status)"

# restore defaults so later checks are unaffected
req PATCH /analytical-engines/organisations/auth-corp/session-settings -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' \
  -d '{"accessTokenTtlSeconds":900,"refreshTokenTtlSeconds":604800,"maxSessionsPerUser":5}'
check "restore session defaults" 200 "$(status)"

# key rotation: a new active key appears, the old token still verifies
req POST /analytical-engines/organisations/auth-corp/keys/rotate -H "Authorization: Bearer $BA"
check "rotate keys" 200 "$(status)"
check "rotated key active" True "$(field '["active"]')"
req GET /analytical-engines/organisations/auth-corp/keys
check "two keys after rotation" 2 "$(jlen)"
req GET /analytical-engines/organisations/auth-corp/users/me -H "Authorization: Bearer $OBA2"
check "old token still verifies after rotation" 200 "$(status)"

# deleting an org with users/keys cascades cleanly
req DELETE /analytical-engines/organisations/auth-corp -H "Authorization: Bearer $BA"
check "delete org with children" 204 "$(status)"
req GET /analytical-engines/organisations/auth-corp -H "Authorization: Bearer $BA"
check "deleted auth org 404" 404 "$(status)"

req PATCH /users/$MID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' -d '{"role":"SUPER_USER"}'
check "promote member" 200 "$(status)"
check "promoted role" SUPER_USER "$(field '["role"]')"

req POST /analytical-engines/users -H "Authorization: Bearer $MA" -H 'Content-Type: application/json' \
  -d '{"firstName":"New","lastName":"Member","email":"newbie@nexx.io","password":"password1"}'
check "promoted member can add (no re-login)" 201 "$(status)"

req PATCH /users/$MID -H "Authorization: Bearer $BA" -H 'Content-Type: application/json' -d '{"enabled":false}'
check "disable member" 200 "$(status)"
req GET /auth/me -H "Authorization: Bearer $MA"
check "disabled token rejected immediately" 401 "$(status)"

req GET /users/$MID -H "Authorization: Bearer $BA"
check "get user by id" 200 "$(status)"

echo "== refresh rotation + reuse detection =="
req POST /auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$BR\"}"
check "refresh rotates" 200 "$(status)"
BR2=$(field '["refreshToken"]')
req POST /auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$BR\"}"
check "reused token rejected" 401 "$(status)"
req POST /auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$BR2\"}"
check "family revoked after reuse" 401 "$(status)"
req POST /auth/logout -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$BR2\"}"
check "logout (idempotent)" 204 "$(status)"

echo "== password change revokes sessions =="
req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"ada@nexx.io","password":"sup3r-secret"}'
check "login" 200 "$(status)"
BA2=$(field '["accessToken"]'); BR3=$(field '["refreshToken"]')
req POST /auth/me/password -H "Authorization: Bearer $BA2" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"sup3r-secret","newPassword":"sup3r-secret2"}'
check "change password" 204 "$(status)"
req POST /auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$BR3\"}"
check "refresh after password change" 401 "$(status)"
req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"ada@nexx.io","password":"sup3r-secret2"}'
check "login new password" 200 "$(status)"
req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"ada@nexx.io","password":"sup3r-secret"}'
check "login old password" 401 "$(status)"
req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"ada@nexx.io","password":"wrong-password"}'
check "login wrong password" 401 "$(status)"

echo "== rate limiting (login bucket: 5/min) =="
req POST /auth/login -H 'Content-Type: application/json' -d '{"email":"ada@nexx.io","password":"wrong-password"}'
check "6th login throttled" 429 "$(status)"
RA=$(header 'Retry-After')
check "retry-after header present" 1 "$([ -n "$RA" ] && echo 1 || echo 0)"
check "429 error shape" 429 "$(field '["status"]')"
check "429 request id matches header" "$(header 'X-Request-Id')" "$(field '["requestId"]')"

echo "== validation + conflicts =="
req POST /auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@nexx.io","password":"sup3r-secret2","platformName":"Analytical Engines"}'
check "duplicate email" 409 "$(status)"
req POST /auth/register -H 'Content-Type: application/json' -d '{}'
check "missing fields" 400 "$(status)"
check "fieldErrors present" True "$(field '["fieldErrors"] != None')"
req POST /auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"A","lastName":"B","email":"short@nexx.io","password":"short","platformName":"Short Pw"}'
check "short password" 400 "$(status)"
req POST /auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"","lastName":"","email":"","password":"","platformName":""}'
check "blank fields" 400 "$(status)"
req POST /auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Burst","lastName":"Burst","email":"burst@nexx.io","password":"password1","platformName":"Burst Co"}'
check "register throttled (6th)" 429 "$(status)"

echo "== security paths =="
req GET /auth/me
check "no token" 401 "$(status)"
check "401 request id matches header" "$(header 'X-Request-Id')" "$(field '["requestId"]')"
req GET /auth/me -H 'Authorization: Bearer garbage.token.here'
check "garbage token" 401 "$(status)"
req GET /nope/does-not-exist -H "Authorization: Bearer $BA2"
check "unknown route (authenticated)" 404 "$(status)"
req GET /nope/does-not-exist
check "unknown route (anonymous)" 401 "$(status)"
req GET /actuator/env
check "actuator env hidden" 401 "$(status)"

echo "== actuator (management port) =="
H=$(curl -s -o "$RESP" -w '%{http_code}' "$MGMT/actuator/health")
check "health" 200 "$H"
check "health UP" UP "$(field '["status"]')"
H=$(curl -s -o "$RESP" -w '%{http_code}' "$MGMT/actuator/info")
check "info" 200 "$H"
check "info app name" nexxauth "$(field '["app"]["name"]')"
check "info app version" 0.0.1-SNAPSHOT "$(field '["app"]["version"]')"
H=$(curl -s -o "$RESP" -w '%{http_code}' "$MGMT/actuator/health/liveness")
check "liveness" 200 "$H"
H=$(curl -s -o "$RESP" -w '%{http_code}' "$MGMT/actuator/health/readiness")
check "readiness" 200 "$H"

if [ -n "$LOG" ]; then
  echo "== audit trail (grep app log) =="
  # the whole run produces several of each event; we only assert presence
  check "org login audited" 1 "$([ "$(grep -c 'AUDIT event=ORG_LOGIN_SUCCESS' "$LOG" || true)" -ge 1 ] && echo 1 || echo 0)"
  check "org key rotation audited" 1 "$([ "$(grep -c 'AUDIT event=ORG_KEY_ROTATED' "$LOG" || true)" -ge 1 ] && echo 1 || echo 0)"
  check "platform login audited" 1 "$([ "$(grep -c 'AUDIT event=PLATFORM_LOGIN_SUCCESS' "$LOG" || true)" -ge 1 ] && echo 1 || echo 0)"
  check "login failure audited" 1 "$([ "$(grep -c 'AUDIT event=.*_LOGIN_FAILURE' "$LOG" || true)" -ge 1 ] && echo 1 || echo 0)"
  check "token reuse audited" 1 "$([ "$(grep -c 'AUDIT event=.*_TOKEN_REUSE' "$LOG" || true)" -ge 1 ] && echo 1 || echo 0)"
fi

echo
echo "=============================="
echo "PASS: $PASS   FAIL: $FAIL"
echo "=============================="
[ "$FAIL" -eq 0 ]
