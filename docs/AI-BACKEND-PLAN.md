# Routinely AI — Secure Integration Plan

## Security rule
Never put an OpenAI API key, provider secret, or privileged token in the Android app, source repository, APK, or client-side configuration.

## Architecture
Android app -> HTTPS backend owned by the project -> AI provider API.

The backend:
- Keeps provider credentials in server-side environment secrets.
- Authenticates app users before personalised requests.
- Validates and limits request size and frequency.
- Sends only the minimum routine context needed for the request.
- Returns structured suggestions for the app to preview.
- Does not change or delete routines without explicit user confirmation.
- Applies timeouts, safe error messages and usage/cost limits.

## Suggested API contract
POST /v1/assistant/chat
Request fields:
- message: user's prompt
- context: optional user-approved routine summaries
- timezone: device timezone identifier

Response fields:
- reply: assistant response
- suggested_actions: optional structured routine suggestions
- request_id: server request identifier

POST /v1/routines/generate
Request fields:
- goal: user's description
- available_time: optional time budget
- preferences: optional schedule preferences

Response:
- routines: structured title, suggested time, repeat rule and category values

## Privacy and UX
- Clearly explain what information is sent when AI is enabled.
- Offer an AI-off mode.
- Keep routine storage and completion usable offline.
- Ask before sending personal routine context.
- Preview AI-generated changes and require confirmation to apply.
- Provide a way to clear AI conversation history.

## Deployment checklist
- Choose and deploy a trusted HTTPS backend.
- Store provider key in backend environment secrets only.
- Add authentication and per-user rate limiting.
- Configure production endpoint through a build configuration value, never a secret.
- Add server-side logging that avoids storing unnecessary personal content.
- Test expired sessions, network failures, rate limits and malformed responses.
