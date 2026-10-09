// 발표 덱 공통 — 테마, 레이아웃, 아이콘, 자주 쓰는 조각. 세 발표가 같은 모양을 쓰도록 여기 모은다.
// 프로젝트 의존성이 아니다 (CLAUDE.md §1.7). 다시 만들 때 필요한 것은 presentations/README.md 참고.
const fs = require("fs");
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");

/** 게임 화면(P1)과 같은 색 — 보라 브랜드, 상승 빨강 · 하락 파랑 (국내 관례) */
const THEME = {
  name: "MoneyGame",
  font: "Malgun Gothic", // 맑은 고딕 — Windows 기본. Mac 에 없으면 비슷한 한글 글꼴로 대체된다. Mac 전용 글꼴은 쓰지 않는다
  colors: {
    dk1: "0B0E14", // 본문 글자
    lt1: "FFFFFF", // 밝은 배경
    dk2: "121722", // 어두운 배경 (표지 · 마무리)
    lt2: "F2F3F7", // 카드 배경
    accent1: "7C5CFF", // 브랜드 보라
    accent2: "F04452", // 상승 빨강
    accent3: "3B82F6", // 하락 파랑
    accent4: "F5B83D", // 경고 · 청산
    accent5: "2FBF71", // 통과
    accent6: "6B7385", // 흐린 글자
    hlink: "7C5CFF",
    folHlink: "5B3FD6",
  },
};

/** hex 만 받는 옵션(차트 색, 그림자 등)에 쓰는 같은 값 */
const HEX = THEME.colors;

const W = 10; // LAYOUT_16x9 = 10" x 5.625"
const H = 5.625;
const M = 0.5; // 바깥 여백

function newDeck({ title, subject, footer }) {
  const pres = new pptxgen();
  pres.layout = "LAYOUT_16x9";
  pres.title = title;
  pres.subject = subject;
  pres.author = "머니게임";
  pres.theme = { headFontFace: THEME.font, bodyFontFace: THEME.font };
  const C = pres.SchemeColor;

  // 표지 · 마무리 (어두운 배경)
  pres.defineSlideMaster({
    title: "DARK",
    background: { color: HEX.dk2 },
    objects: [
      { placeholder: { options: { name: "kicker", type: "body", x: M, y: 1.35, w: 6.2, h: 0.4, fontSize: 14, bold: true, color: C.accent1, align: "left", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "title", type: "title", x: M, y: 1.8, w: 6.4, h: 1.5, fontSize: 40, bold: true, color: C.background1, align: "left", valign: "top", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "body", type: "body", x: M, y: 3.45, w: 6.2, h: 0.9, fontSize: 16, color: C.background2, align: "left", valign: "top", margin: 0 }, text: "" } },
      // 발표자 이름 자리 — 비워 두면 편집 화면에만 안내 문구가 보이고, 슬라이드 쇼에는 나오지 않는다
      { placeholder: { options: { name: "presenter", type: "body", x: M, y: 4.55, w: 4.0, h: 0.4, fontSize: 15, bold: true, color: C.background1, align: "left", valign: "middle", margin: 0 }, text: "발표자 이름" } },
    ],
  });

  // 정리 (어두운 배경, 제목 위쪽)
  pres.defineSlideMaster({
    title: "DARK_TOP",
    background: { color: HEX.dk2 },
    objects: [
      { placeholder: { options: { name: "kicker", type: "body", x: M, y: 0.45, w: 6.0, h: 0.35, fontSize: 14, bold: true, color: C.accent1, align: "left", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "title", type: "title", x: M, y: 0.82, w: 6.0, h: 0.75, fontSize: 32, bold: true, color: C.background1, align: "left", valign: "top", margin: 0 }, text: "" } },
    ],
  });

  // 본문 (밝은 배경, 제목만)
  pres.defineSlideMaster({
    title: "CONTENT",
    background: { color: HEX.lt1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: M, y: 0.32, w: W - 2 * M, h: 0.62, fontSize: 30, bold: true, color: C.text1, align: "left", valign: "middle", margin: 0 }, text: "" } },
      { placeholder: { options: { name: "lead", type: "body", x: M, y: 0.94, w: W - 2 * M, h: 0.36, fontSize: 14, color: C.accent6, align: "left", valign: "top", margin: 0 }, text: "" } },
      { text: { text: footer, options: { x: M, y: 5.25, w: 5, h: 0.22, fontSize: 9, color: C.accent6, margin: 0 } } },
    ],
    slideNumber: { x: W - M - 0.6, y: 5.25, w: 0.6, h: 0.22, fontSize: 9, color: HEX.accent6, align: "right", margin: 0 },
  });

  return { pres, C };
}

/** react-icons 아이콘을 PNG data URI 로 */
async function icon(Component, hex = "FFFFFF") {
  if (!Component) throw new Error("아이콘 이름이 틀렸습니다");
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Component, { color: `#${hex}`, size: 256 }));
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  return `image/png;base64,${png.toString("base64")}`;
}

/** 조각 그리기. 모든 텍스트는 텍스트 상자로, 기본 여백 0 */
function kit(pres, C) {
  const S = pres.shapes;
  return {
    text(slide, text, o) {
      slide.addText(text, { isTextBox: true, margin: 0, fontSize: 14, color: C.text1, valign: "top", ...o });
    },
    card(slide, { x, y, w, h, fill = C.background2, line, name, shadow = false }) {
      slide.addShape(S.ROUNDED_RECTANGLE, {
        x, y, w, h, rectRadius: 0.08, objectName: name,
        fill: { color: fill },
        line: line ? { color: line, width: 1.25 } : { type: "none" },
        shadow: shadow ? { type: "outer", color: "000000", opacity: 0.1, blur: 8, offset: 2, angle: 90 } : undefined,
      });
    },
    badge(slide, data, { x, y, d = 0.5, fill = C.accent1, name }) {
      slide.addShape(S.OVAL, { x, y, w: d, h: d, fill: { color: fill }, line: { type: "none" }, objectName: name });
      const p = d * 0.25;
      slide.addImage({ data, x: x + p, y: y + p, w: d - 2 * p, h: d - 2 * p, objectName: name && `${name}-icon` });
    },
    /** 번호가 든 동그라미 */
    num(slide, n, { x, y, d = 0.4, fill = C.accent1, color = C.background1 }) {
      slide.addShape(S.OVAL, { x, y, w: d, h: d, fill: { color: fill }, line: { type: "none" } });
      slide.addText(String(n), { isTextBox: true, x, y, w: d, h: d, margin: 0, align: "center", valign: "middle", fontSize: 14, bold: true, color });
    },
    arrow(slide, x1, y1, x2, y2, color = C.accent6) {
      slide.addShape(S.LINE, {
        x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1) || 0.001, h: Math.abs(y2 - y1) || 0.001,
        flipH: x2 < x1, flipV: y2 < y1,
        line: { color, width: 1.5, endArrowType: "triangle" },
      });
    },
    rect(slide, o) {
      slide.addShape(S.RECTANGLE, { line: { type: "none" }, ...o });
    },
    line(slide, o) {
      slide.addShape(S.LINE, o);
    },
    S,
  };
}

/**
 * 파일을 쓰고 테마 색을 넣는다. pptxgenjs 는 테마 색을 쓰지 못하므로 theme1.xml 을 직접 고친다.
 * 한글은 테마의 동아시아(ea) 글꼴을 따르므로 그 자리도 채운다.
 */
async function finish(pres, fileName) {
  await pres.writeFile({ fileName });
  const JSZip = require(require.resolve("jszip", { paths: [require.resolve("pptxgenjs")] }));
  const zip = await JSZip.loadAsync(fs.readFileSync(fileName));
  const part = "ppt/theme/theme1.xml";
  const slots = ["dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"];
  const scheme = `<a:clrScheme name="${THEME.name}">${slots.map((k) => `<a:${k}><a:srgbClr val="${THEME.colors[k]}"/></a:${k}>`).join("")}</a:clrScheme>`;
  let xml = await zip.file(part).async("string");
  xml = xml
    .replace(/<a:clrScheme\b[\s\S]*?<\/a:clrScheme>/, () => scheme)
    .replace(/(<a:(?:theme|fontScheme)\b[^>]*?\bname=")[^"]*"/g, (_, head) => `${head}${THEME.name}"`)
    .replace(/<a:ea typeface="[^"]*"\/>/g, `<a:ea typeface="${THEME.font}"/>`);
  if (!xml.includes(scheme)) throw new Error("테마 색을 넣지 못했습니다");
  zip.file(part, xml);
  for (const name of Object.keys(zip.files)) {
    if (!name.endsWith(".xml")) continue;
    const bad = (await zip.file(name).async("string")).match(/<a:srgbClr val="((?![0-9A-Fa-f]{6}")[^"]*)"/);
    if (bad) throw new Error(`${name}: hex 가 아닌 색 "${bad[1]}" — hex 만 받는 옵션에 scheme 색을 넣었다`);
  }
  fs.writeFileSync(fileName, await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" }));
}

module.exports = { THEME, HEX, W, H, M, newDeck, icon, kit, finish };
