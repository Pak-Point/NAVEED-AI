# NAVEED AI

NAVEED AI is a mobile-friendly web assistant/PWA for Urdu, Roman Urdu, Saraiki and English. The Android project remains separate and is not changed by this web deployment.

## Host the web app on GitHub Pages

1. In the repository, open **Settings → Pages**.
2. Under **Build and deployment**, choose **GitHub Actions**.
3. The workflow in `.github/workflows/pages.yml` publishes the web app.
4. Expected project-site URL: `https://pak-point.github.io/NAVEED-AI/` after Pages is enabled and the workflow succeeds.

## Secure Gemini backend on Cloudflare Workers

1. Create a Cloudflare account and open **Workers & Pages → Create → Worker**.
2. Deploy the code from `cloudflare/worker.js` (or use Wrangler with `wrangler.toml`).
3. In Worker **Settings → Variables and Secrets**, add secret `GEMINI_API_KEY`. Never commit or share the key.
4. Set the frontend `API_URL` in `index.html` to the deployed Worker URL.
5. The Worker currently permits browser requests only from `https://pak-point.github.io`.

## Notes

- GitHub Pages serves static files only; Cloudflare Worker securely calls Gemini.
- Browser microphone support depends on Chrome/ChromeOS permissions and supported speech APIs.
- A browser PWA cannot provide Android-only functions such as torch control or guaranteed always-on background wake-word listening.
- Hosting and Gemini usage are subject to provider quotas and terms.
