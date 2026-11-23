// Fotografa ogni SVG di una cartella in PNG a 3x (il Chromium preinstallato, via Playwright).
//   node tools/rendi_svg.js CARTELLA_SVG CARTELLA_PNG
// make_art.py poi riduce i PNG a 440x250 a 1 bit (dithering di Atkinson).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const [src, out] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 440, height: 250 }, deviceScaleFactor: 3 });
  const files = fs.readdirSync(src).filter((f) => f.endsWith('.svg')).sort();
  for (const f of files) {
    const dst = path.join(out, f.replace(/\.svg$/, '.png'));
    const svgPath = path.join(src, f);
    if (fs.existsSync(dst) && fs.statSync(dst).mtimeMs >= fs.statSync(svgPath).mtimeMs) continue;
    const svg = fs.readFileSync(svgPath, 'utf8');
    await page.setContent(`<!doctype html><html><body style="margin:0;background:#fff">${svg}</body></html>`);
    await page.screenshot({ path: dst, clip: { x: 0, y: 0, width: 440, height: 250 } });
  }
  await browser.close();
  console.log(`${files.length} svg`);
})();
