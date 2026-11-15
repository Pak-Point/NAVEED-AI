# NAVEED AI

A mobile-friendly personal AI chat app with browser speech input and spoken replies.

## Deploy with Netlify

1. Import this GitHub repository into Netlify.
2. Set the build publish directory to `.` and functions directory to `netlify/functions` (also configured in `netlify.toml`).
3. In Netlify, open **Project configuration → Environment variables**.
4. Add `GEMINI_API_KEY` with your Gemini API key. Never commit the key to GitHub or put it in browser code.
5. Trigger a new deploy after adding the variable.
6. Open the deployed site over HTTPS and test chat and microphone permissions.

The backend uses Google's Gemini Interactions REST API. The browser's speech recognition support varies by browser and device; spoken replies use the browser's speech synthesis.

## Security notes

- Keep `GEMINI_API_KEY` only in Netlify's server-side environment variables.
- Add rate limiting and authentication before sharing this public app widely.
- Free API and hosting quotas may be limited and can change.
