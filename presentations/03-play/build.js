// 발표 — 직접 플레이하며 다듬은 것들 (중간 점검, 15분 안팎, 12장)
// 범위: 게임 화면 · 결과 화면, 존버 봇, 여러 사람과 실제 플레이(임시 공개), 닉네임 보호, 분봉 동결,
//   플레이 피드백 반영, 운영 사고와 복구. 구현 · 코드 이야기는 넣지 않는다.
// 말투 (2026-10-10 사용자): 슬라이드는 전부 개조식. 발표자 노트는 읽는 대본이라 합니다체.
//   긴 줄표, 꺾쇠괄호, 화살표 잇기는 쓰지 않는다. 영어 뒤 조사는 붙여 쓴다.
// 화면 캡처(assets/)는 2026-10-10 실제 서버로 한 판(발표용 vs 존버봇, 개발용 일봉 시나리오, 120틱)을 하며 찍었다.
// 근거는 CLAUDE.md §3 M9 · §4 P1 · §8 · §9-12 · §9-14 · §2.2 · §9 「알려진 작은 문제」 다.
//   NODE_PATH=<도구 폴더>/node_modules node presentations/03-play/build.js
const path = require("path");
const fa = require("react-icons/fa6");
const { HEX, W, M, newDeck, icon, kit, finish } = require("../common/deck-kit");

const OUT = path.join(__dirname, "moneygame-play.pptx");
const ASSET = (f) => path.join(__dirname, "assets", f);
const SHOT_W = 1412, SHOT_H = 757; // 캡처 크기 (px)

async function main() {
  const { pres, C } = newDeck({
    title: "머니게임: 직접 플레이하며 다듬은 것들",
    subject: "게임 화면, 존버 봇, 실제 대전, 플레이로 내린 결정",
    footer: "머니게임 | 직접 플레이하며 다듬은 것들",
  });
  const k = kit(pres, C);
  const S = pres.shapes;
  const I = {};
  for (const [key, comp] of Object.entries({
    screen: fa.FaDisplay, robot: fa.FaRobot, globe: fa.FaGlobe, decide: fa.FaChartColumn,
    palette: fa.FaPalette, history: fa.FaClockRotateLeft, lines: fa.FaGripLines, eye: fa.FaEyeSlash,
    coin: fa.FaCoins, plug: fa.FaPlug, lock: fa.FaLock, user: fa.FaUserCheck,
    clock: fa.FaStopwatch, door: fa.FaDoorOpen, rotate: fa.FaRotate, timer: fa.FaHourglassHalf,
  })) {
    I[key] = await icon(comp);
  }

  const content = (section, title, lead) => {
    const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: section });
    s.addText(title, { placeholder: "title" });
    s.addText(lead, { placeholder: "lead" });
    return s;
  };
  /** 캡처 위 번호 표시 — px 좌표를 슬라이드 좌표로 */
  const marker = (s, n, img, px, py) => {
    const sc = img.w / SHOT_W;
    const x = img.x + px * sc, y = img.y + py * sc, d = 0.32;
    s.addShape(S.OVAL, { x: x - d / 2, y: y - d / 2, w: d, h: d, fill: { color: C.accent4 }, line: { color: C.background1, width: 1.5 } });
    s.addText(String(n), { isTextBox: true, x: x - d / 2, y: y - d / 2, w: d, h: d, margin: 0, align: "center", valign: "middle", fontSize: 12, bold: true, color: C.text1 });
  };
  const legend = (s, items, x, y, w, gapY = 0.66) => {
    items.forEach(([t, d], i) => {
      const yy = y + i * gapY;
      k.num(s, i + 1, { x, y: yy, d: 0.32, fill: C.accent4, color: C.text1 });
      k.text(s, t, { x: x + 0.42, y: yy - 0.02, w: w - 0.42, h: 0.28, fontSize: 12, bold: true });
      k.text(s, d, { x: x + 0.42, y: yy + 0.25, w: w - 0.42, h: 0.36, fontSize: 10, color: C.accent6 });
    });
  };

  // ───────────────────────── 1. 표지 ─────────────────────────
  pres.addSection({ title: "소개" });
  {
    const s = pres.addSlide({ masterName: "DARK", sectionTitle: "소개" });
    s.addText("머니게임 | 중간 점검", { placeholder: "kicker" });
    s.addText("직접 플레이하며\n다듬은 것들", { placeholder: "title" });
    s.addText("게임 화면, 존버 봇, 여러 사람과의 실제 대전\n플레이 결과로 내린 결정", { placeholder: "body" });
    s.addText("", { placeholder: "presenter" });
    // 오른쪽 — 실제 게임 화면 미리보기
    const w = 3.2, h = w * SHOT_H / SHOT_W;
    s.addShape(S.ROUNDED_RECTANGLE, { x: 6.25, y: 1.55, w: w + 0.12, h: h + 0.12, rectRadius: 0.06, fill: { color: "242E42" }, line: { type: "none" } });
    s.addImage({ path: ASSET("game.jpg"), x: 6.31, y: 1.61, w, h, objectName: "cover-shot" });
    s.addNotes(
      "머니게임은 여러 명이 같은 방에서 실제 과거 시세를 빠르게 재생하며 거래하고, 끝날 때 남은 자산으로 순위를 가리는 웹게임입니다. " +
        "오늘은 게임 화면과 봇을 붙여 실제로 플레이해 보고, 다른 사람들과 함께 플레이하면서 발견한 문제와 데이터로 내린 결정을 말씀드리겠습니다."
    );
  }

  // ───────────────────────── 2. 개요 ─────────────────────────
  {
    const s = content("소개", "개요: 규칙과 서버 위에 화면과 플레이", "시세 데이터, 게임 규칙, 실시간 서버는 완료. 그 위에 화면과 봇을 얹고 실제로 플레이하며 다듬음");
    const cols = [
      [I.screen, "플레이 가능한 게임", ["게임 화면과 결과 화면", "존버 봇으로 혼자서도 대결", "끊겨도 이어지는 재접속"]],
      [I.globe, "여러 사람과 실제 플레이", ["임시 주소로 외부 접속 허용", "다른 사람과 같은 방에서 완주", "닉네임 도용 문제 발견과 보호"]],
      [I.decide, "플레이로 내린 결정", ["분봉 모드 동결", "틱 속도와 판 길이 조정", "버려진 대기방 자동 정리"]],
    ];
    const cw = 2.85, gap = 0.225;
    cols.forEach(([ic, t, items], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 1.55, w: cw, h: 3.4, name: `col-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: 1.77, d: 0.55 });
      k.text(s, t, { x: x + 0.22, y: 2.48, w: cw - 0.44, h: 0.35, fontSize: 16, bold: true });
      items.forEach((it, j) => {
        const y = 3.0 + j * 0.58;
        k.rect(s, { x: x + 0.24, y: y + 0.1, w: 0.08, h: 0.08, fill: { color: C.accent1 } });
        k.text(s, it, { x: x + 0.42, y, w: cw - 0.64, h: 0.5, fontSize: 12 });
      });
    });
    s.addNotes(
      "지금까지 시세 데이터와 게임 규칙, 실시간 서버를 만들었고, 그 위에 실제 게임 화면과 봇을 얹었습니다. " +
        "그리고 다른 사람들과 실제로 플레이해 보면서 문제를 찾고 결정을 내렸습니다. 오늘은 이 세 가지, 플레이 가능한 게임, 여러 사람과의 실제 플레이, 플레이로 내린 결정 순서로 말씀드리겠습니다."
    );
  }

  // ───────────────────────── 3. 게임 화면 ─────────────────────────
  pres.addSection({ title: "플레이 가능한 게임" });
  {
    const s = content("플레이 가능한 게임", "게임 화면: 한 화면에서 보고, 판단하고, 주문", "실제 한 판 중 캡처 (52틱 시점, 종목 A에 배율 ×3 보유)");
    const img = { x: M, y: 1.45, w: 6.35 };
    img.h = img.w * SHOT_H / SHOT_W;
    s.addShape(S.ROUNDED_RECTANGLE, { x: img.x - 0.04, y: img.y - 0.04, w: img.w + 0.08, h: img.h + 0.08, rectRadius: 0.04, fill: { color: C.text2 }, line: { type: "none" } });
    s.addImage({ path: ASSET("game.jpg"), x: img.x, y: img.y, w: img.w, h: img.h, objectName: "game-shot" });
    marker(s, 1, img, 30, 128);
    marker(s, 2, img, 30, 228);
    marker(s, 3, img, 30, 395);
    marker(s, 4, img, 1040, 228);
    marker(s, 5, img, 1040, 700);
    legend(s, [
      ["남은 시간, 총자산, 순위", "진행 막대와 함께 상단 고정"],
      ["종목 카드 A, B, C", "현재가, 시작 대비 등락, 미니 차트"],
      ["캔들 차트", "시작 전 60봉, 매수 지점\n진입가와 청산가 선"],
      ["주문", "증거금 빠른 선택 10, 25, 50%, 최대"],
      ["순위", "5초마다, 남의 포지션과 배율 포함"],
    ], 7.05, 1.48, W - M - 7.05, 0.74);
    s.addNotes(
      "실제 한 판을 하면서 찍은 게임 화면입니다. 위쪽에 남은 시간과 총자산, 순위가 있고, 그 아래 종목 카드에서 세 종목의 흐름을 한눈에 봅니다. " +
        "가운데 차트에는 시작 전 60봉이 흐리게 깔리고, 제가 산 지점과 진입가, 청산가 선이 표시됩니다. 오른쪽에서 증거금과 배율을 골라 바로 주문하고, 아래 순위에서는 다른 사람이 어떤 종목에 몇 배를 걸었는지까지 보입니다."
    );
  }

  // ───────────────────────── 4. 화면 설계 결정 ─────────────────────────
  {
    const s = content("플레이 가능한 게임", "화면 설계 결정 6가지", "보기 좋은 것보다 판단에 필요한 것 우선");
    const items = [
      [I.palette, "상승 빨강, 하락 파랑", "국내 증권 화면 관례"],
      [I.history, "시작 전 60봉 표시", "0틱에도 추세를 보고 판단\n시작 전 봉은 흐리게"],
      [I.lines, "진입가와 청산가 선", "내 포지션의 청산 지점을\n차트에서 바로 확인"],
      [I.eye, "게임 중 날짜 숨김", "날짜로 종목 추정 방지\n결과 화면에서 공개"],
      [I.coin, "금액은 서버 값 그대로", "화면에서 다시 계산하지 않아\n소수 오차 없음"],
      [I.plug, "끊겨도 이어짐", "2초 뒤 자동 재연결\n지금까지의 차트 복원"],
    ];
    const cw = 2.85, ch = 1.6, gap = 0.225;
    items.forEach(([ic, t, d], i) => {
      const x = M + (i % 3) * (cw + gap);
      const y = 1.5 + Math.floor(i / 3) * (ch + 0.2);
      k.card(s, { x, y, w: cw, h: ch, name: `ui-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: y + 0.2, d: 0.48 });
      k.text(s, t, { x: x + 0.82, y: y + 0.2, w: cw - 1.0, h: 0.48, fontSize: 13, bold: true, valign: "middle" });
      k.text(s, d, { x: x + 0.22, y: y + 0.82, w: cw - 0.44, h: 0.68, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "화면을 만들 때 보기 좋은 것보다 판단에 필요한 것을 먼저 챙겼습니다. 색은 국내 증권 화면처럼 오르면 빨강, 내리면 파랑입니다. " +
        "시작 전 60봉을 보여줘서 첫 틱부터 추세를 보고 판단할 수 있고, 내 포지션의 진입가와 청산가를 차트에 선으로 그려 어디서 청산되는지 바로 보이게 했습니다. " +
        "게임 중에는 날짜를 숨겨 종목을 알아맞히지 못하게 했고, 금액은 서버가 계산한 값을 그대로 보여줘 오차가 없습니다. 연결이 끊겨도 자동으로 다시 붙어 차트가 그대로 이어집니다."
    );
  }

  // ───────────────────────── 5. 결과 화면 ─────────────────────────
  {
    const s = content("플레이 가능한 게임", "결과 화면: 종목 공개와 매매 복기", "같은 판의 결과. 끝나는 순간 실제 종목과 기간 공개, 내 전적에서 다시 보기 가능");
    const img = { x: M, y: 1.45, w: 6.35 };
    img.h = img.w * SHOT_H / SHOT_W;
    s.addShape(S.ROUNDED_RECTANGLE, { x: img.x - 0.04, y: img.y - 0.04, w: img.w + 0.08, h: img.h + 0.08, rectRadius: 0.04, fill: { color: C.text2 }, line: { type: "none" } });
    s.addImage({ path: ASSET("result.jpg"), x: img.x, y: img.y, w: img.w, h: img.h, objectName: "result-shot" });
    marker(s, 1, img, 30, 150);
    marker(s, 2, img, 30, 235);
    marker(s, 3, img, 30, 340);
    marker(s, 4, img, 1020, 340);
    legend(s, [
      ["실제 기간 공개", "짧은 판은 실제로 쓴 구간만 표시"],
      ["종목 정체 공개", "A, B, C의 실제 종목명\n기간 등락률"],
      ["실제 날짜 차트와 매매 지점", "매수, 매도, 청산, 종료 정리 표시\n내 매매만 또는 모두 보기"],
      ["최종 순위와 수익률", "거래 횟수, 청산 횟수 포함"],
    ], 7.05, 1.5, W - M - 7.05, 0.82);
    s.addNotes(
      "같은 판의 결과 화면입니다. 판이 끝나는 순간 A, B, C가 실제로 어떤 종목이었는지와 실제 기간을 공개합니다. " +
        "차트는 이제 실제 날짜로 그려지고, 내가 사고판 지점이 표시돼서 어디서 잘했고 어디서 놓쳤는지 복기할 수 있습니다. 지난 판들은 로비의 내 전적에서 다시 열어 볼 수 있습니다."
    );
  }

  // ───────────────────────── 6. 존버 봇 ─────────────────────────
  {
    const s = content("플레이 가능한 게임", "존버 봇: 시장을 그대로 따라간 기준선", "혼자서도 플레이 가능, 봇을 이기면 판단이 시장보다 나았다는 뜻");
    k.card(s, { x: M, y: 1.5, w: 4.3, h: 3.5, fill: C.background2, name: "bot-rule" });
    k.badge(s, I.robot, { x: M + 0.25, y: 1.7, d: 0.55 });
    k.text(s, "봇의 규칙", { x: M + 0.95, y: 1.7, w: 2.5, h: 0.55, fontSize: 16, bold: true, valign: "middle" });
    const rules = [
      ["0틱에 전 종목 매수", "현금을 종목 수로 똑같이 나눔, 배율 1"],
      ["끝까지 보유", "판 종료 정리로만 매도, 수수료도 똑같이 부담"],
      ["사람과 같은 주문 경로", "규칙상 특혜 없음, 엔진 입장에서 사람과 구분 불가"],
      ["방 만들 때 0 ~ 3개", "혼자 하려면 봇 1개"],
    ];
    rules.forEach(([t, d], i) => {
      const y = 2.45 + i * 0.62;
      k.text(s, t, { x: M + 0.25, y, w: 3.8, h: 0.28, fontSize: 12, bold: true });
      k.text(s, d, { x: M + 0.25, y: y + 0.27, w: 3.8, h: 0.28, fontSize: 10.5, color: C.accent6 });
    });
    const rx = 5.1, rw = W - M - rx;
    k.text(s, "실제 한 판 (급등 구간, 120틱)", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 13, bold: true });
    const players = [
      ["1", "존버봇", "세 종목 균등, 배율 1", "+97.98%", 97.98],
      ["2", "발표용", "종목 A에 현금 25%, 배율 3", "+47.17%", 47.17],
    ];
    players.forEach(([r, n, how, ret, v], i) => {
      const y = 1.95 + i * 1.05;
      k.card(s, { x: rx, y, w: rw, h: 0.92, name: `p-${r}` });
      k.text(s, r, { x: rx + 0.15, y, w: 0.35, h: 0.92, fontSize: 18, bold: true, color: r === "1" ? C.accent4 : C.accent6, valign: "middle" });
      k.text(s, [
        { text: n, options: { bold: true, breakLine: true } },
        { text: how, options: { fontSize: 10.5, color: C.accent6 } },
      ], { x: rx + 0.5, y: y + 0.1, w: 2.3, h: 0.72, fontSize: 13, valign: "middle" });
      k.text(s, ret, { x: rx + rw - 1.5, y, w: 1.35, h: 0.92, fontSize: 20, bold: true, color: C.accent2, align: "right", valign: "middle" });
    });
    k.card(s, { x: rx, y: 4.15, w: rw, h: 0.85, fill: C.background2, name: "bot-insight" });
    k.text(s, [
      { text: "상승장에서는 다 사서 버티는 쪽이 강함", options: { bold: true, breakLine: true } },
      { text: "봇은 쉬운 상대가 아니라 넘어야 할 기준선", options: { fontSize: 11, color: C.accent6 } },
    ], { x: rx + 0.2, y: 4.22, w: rw - 0.4, h: 0.72, fontSize: 13 });
    s.addNotes(
      "혼자서도 플레이할 수 있도록 존버 봇을 넣었습니다. 봇은 시작하자마자 현금을 세 종목에 똑같이 나눠 배율 1로 사고 끝까지 들고 갑니다. 사람과 같은 주문 경로를 쓰기 때문에 규칙상 특혜는 없습니다. " +
        "봇의 결과는 아무것도 안 하고 시장을 따라갔을 때의 결과라서, 봇을 이기면 내 판단이 시장보다 나았다는 뜻이 됩니다. " +
        "실제로 한 판 해 보니 세 종목이 모두 오른 구간에서는 봇이 98% 수익으로 저를 이겼습니다. 봇은 쉬운 상대가 아니라 넘어야 할 기준선입니다."
    );
  }

  // ───────────────────────── 7. 여러 사람과 실제 플레이 ─────────────────────────
  pres.addSection({ title: "실제 플레이" });
  {
    const s = content("실제 플레이", "여러 사람과 실제 플레이: 임시 공개", "정식 배포 전, 개발 PC를 임시 주소로 열어 다른 사람과 같은 방에서 플레이");
    const flow = [
      ["다른 사람의 브라우저", "인터넷 어디서나\n같은 와이파이면 IP 주소로도"],
      ["임시 공개 주소", "실행할 때마다 새 주소\n암호화된 연결"],
      ["개발 PC", "화면 파일만 공개\n서버는 PC 안에서만 접근"],
    ];
    const bw = 2.6, gap = 0.55;
    flow.forEach(([t, d], i) => {
      const x = M + i * (bw + gap);
      k.card(s, { x, y: 1.55, w: bw, h: 1.25, fill: i === 2 ? C.accent1 : C.background2, name: `share-${i + 1}` });
      k.text(s, t, { x: x + 0.15, y: 1.68, w: bw - 0.3, h: 0.32, fontSize: 14, bold: true, align: "center", color: i === 2 ? C.background1 : C.text1 });
      k.text(s, d, { x: x + 0.15, y: 2.05, w: bw - 0.3, h: 0.65, fontSize: 11, align: "center", color: i === 2 ? C.background2 : C.accent6 });
      if (i < flow.length - 1) k.arrow(s, x + bw + 0.08, 2.17, x + bw + gap - 0.08, 2.17);
    });
    const checks = [
      ["공개 전 점검", "소스 코드와 설정 파일 주소로 접근해도 열리지 않음 확인\n다른 사이트에서 온 실시간 연결은 거부"],
      ["실제 플레이 결과", "다른 사람과 같은 방에서 한 판 완주\n틱, 주문, 순위, 청산 알림 모두 정상"],
    ];
    checks.forEach(([t, d], i) => {
      const x = M + i * 4.6;
      k.card(s, { x, y: 3.1, w: 4.4, h: 1.25, name: `check-${i + 1}` });
      k.text(s, t, { x: x + 0.22, y: 3.22, w: 3.96, h: 0.3, fontSize: 14, bold: true, color: C.accent1 });
      k.text(s, d, { x: x + 0.22, y: 3.57, w: 3.96, h: 0.7, fontSize: 11, color: C.accent6 });
    });
    k.text(s, "한계: PC가 켜져 있는 동안만 접속 가능, 계정이 없어 닉네임만으로 입장", { x: M, y: 4.6, w: W - 2 * M, h: 0.3, fontSize: 11, color: C.accent6 });
    s.addNotes(
      "정식으로 배포하기 전에 다른 사람과 실제로 플레이해 보려고, 개발 PC를 임시 주소로 열었습니다. 인터넷 어디서나 그 주소로 들어올 수 있고, 같은 와이파이에서는 IP 주소로도 접속할 수 있습니다. " +
        "공개하기 전에 소스 코드나 설정 파일이 밖에서 열리지 않는지, 다른 사이트에서 오는 실시간 연결이 거부되는지 확인했습니다. " +
        "그 결과 다른 사람과 같은 방에서 한 판을 끝까지 플레이했고, 이 과정에서 다음 장의 문제를 발견했습니다."
    );
  }

  // ───────────────────────── 8. 닉네임 보호 ─────────────────────────
  {
    const s = content("실제 플레이", "발견한 문제: 같은 닉네임이면 남의 전적으로 입장", "비밀번호 없이 닉네임만으로 입장하는 설계에서 생긴 문제, 보호 방식 3가지 비교");
    const hdr = (t) => ({ text: t, options: { bold: true, color: HEX.lt1, fill: { color: HEX.accent1 }, align: "center" } });
    const cell = (t, o = {}) => ({ text: t, options: { valign: "middle", ...o } });
    s.addTable(
      [
        [hdr("방식"), hdr("장점"), hdr("단점")],
        [cell("브라우저 토큰 (채택)", { bold: true, color: HEX.accent1 }), cell("비밀번호 없이 지금처럼 입력\n처음 만든 브라우저만 사용"), cell("다른 기기, 브라우저 데이터 삭제 시 사용 불가")],
        [cell("닉네임 + 비밀번호", { bold: true }), cell("어느 기기에서나 같은 닉네임"), cell("계정 기능 전체 필요, 작업 규모 큼")],
        [cell("접속 중일 때만 막기", { bold: true }), cell("가장 간단"), cell("접속하지 않은 동안은 누구나 사용")],
      ],
      { x: M, y: 1.5, w: W - 2 * M, colW: [2.3, 3.3, 3.4], rowH: [0.38, 0.6, 0.5, 0.5], fontSize: 12, color: HEX.dk1, border: { type: "solid", pt: 0.75, color: "D9DCE3" } }
    );
    const pts = [
      [I.lock, "비밀 값은 해시만 저장", "브라우저에 준 원문은 서버에 남기지 않음"],
      [I.user, "기존 닉네임은 선착순", "먼저 들어온 브라우저가 소유\n동시에 와도 한 곳만 성공"],
      [I.door, "다른 브라우저에서 같은 닉네임", "이미 사용 중인 닉네임 안내 후 입장 거부"],
    ];
    const cw = 2.85, gap = 0.225;
    pts.forEach(([ic, t, d], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 3.75, w: cw, h: 1.25, name: `nick-${i + 1}` });
      k.badge(s, ic, { x: x + 0.2, y: 3.9, d: 0.42 });
      k.text(s, t, { x: x + 0.72, y: 3.88, w: cw - 0.85, h: 0.46, fontSize: 11.5, bold: true, valign: "middle" });
      k.text(s, d, { x: x + 0.2, y: 4.45, w: cw - 0.4, h: 0.48, fontSize: 10.5, color: C.accent6 });
    });
    s.addNotes(
      "여러 사람이 플레이하자 문제가 하나 드러났습니다. 비밀번호 없이 닉네임만으로 입장하게 만들었더니, 다른 사람이 같은 닉네임을 치면 그 사람 전적으로 들어가 버렸습니다. " +
        "세 가지 방법을 비교했습니다. 비밀번호 계정은 가장 확실하지만 작업이 크고, 접속 중일 때만 막는 방법은 간단하지만 허점이 큽니다. " +
        "그래서 처음 닉네임을 만든 브라우저에 비밀 값을 주고, 그 브라우저에서만 그 닉네임을 쓸 수 있게 했습니다. 지금처럼 닉네임만 치면 되고, 다른 브라우저에서는 이미 사용 중이라고 안내합니다."
    );
  }

  // ───────────────────────── 9. 분봉 동결 ─────────────────────────
  pres.addSection({ title: "플레이로 내린 결정" });
  {
    const s = content("플레이로 내린 결정", "데이터로 내린 결정: 분봉 모드 동결", "플레이 체감은 일봉보다 재미 덜함, 데이터로 원인 확인");
    // 후보별 최대 낙폭 — 아래로 내려가는 막대, −10% 청산선
    const cands = [["08-31", 0], ["08-26", -0.18], ["09-08", -3.19], ["09-01", -0.68], ["08-27", -3.98], ["08-28", -3.01]];
    const gx = M + 0.55, gw = 4.9, base = 2.0, perPct = 0.26; // 1% = 0.26"
    k.text(s, "분봉 후보 6개의 시작 대비 최대 낙폭", { x: M, y: 1.45, w: 5.4, h: 0.3, fontSize: 13, bold: true });
    k.line(s, { x: gx, y: base, w: gw, h: 0, line: { color: C.accent6, width: 1 } });
    k.text(s, "0%", { x: M, y: base - 0.12, w: 0.5, h: 0.24, fontSize: 9, color: C.accent6, align: "right" });
    const liqY = base + 10 * perPct;
    k.line(s, { x: gx, y: liqY, w: gw, h: 0, line: { color: C.accent2, width: 1.5, dashType: "dash" } });
    k.text(s, "−10%", { x: M, y: liqY - 0.12, w: 0.5, h: 0.24, fontSize: 9, color: C.accent2, align: "right" });
    k.text(s, "배율 ×10 청산선", { x: gx + gw - 1.6, y: liqY + 0.04, w: 1.6, h: 0.24, fontSize: 10, bold: true, color: C.accent2, align: "right" });
    const slot = gw / cands.length, bw = 0.42;
    cands.forEach(([d, v], i) => {
      const cx = gx + slot * i + slot / 2;
      const hh = Math.max(-v * perPct, 0.02);
      k.rect(s, { x: cx - bw / 2, y: base, w: bw, h: hh, fill: { color: C.accent1 }, objectName: `dd-${d}` });
      k.text(s, v === 0 ? "0%" : `${v.toFixed(2)}%`, { x: cx - 0.45, y: base + hh + 0.03, w: 0.9, h: 0.22, fontSize: 10, bold: true, align: "center" });
      k.text(s, d, { x: cx - 0.4, y: base - 0.3, w: 0.8, h: 0.22, fontSize: 9, color: C.accent6, align: "center" });
    });
    k.text(s, "2026-08-26 ~ 09-08 정규장, 겹치지 않는 후보. 세 종목 중 가장 많이 내린 값", { x: M, y: 4.85, w: 5.4, h: 0.22, fontSize: 9, color: C.accent6 });
    const rx = 6.2, rw = W - M - rx;
    const pts = [
      ["청산이 한 번도 안 남", "가장 큰 낙폭 −3.98%, 최대 배율 ×10의 청산선 −10%"],
      ["배율이 위험 없는 손익 확대가 됨", "항상 최대 배율이 정답, 팔지 말지 고민이 사라짐"],
      ["데이터로도 해결 어려움", "분봉 데이터 14일치, 더 받을 수 없는 상황"],
    ];
    pts.forEach(([t, d], i) => {
      const y = 1.5 + i * 0.95;
      k.num(s, i + 1, { x: rx, y, d: 0.34 });
      k.text(s, t, { x: rx + 0.46, y: y - 0.02, w: rw - 0.46, h: 0.3, fontSize: 12.5, bold: true });
      k.text(s, d, { x: rx + 0.46, y: y + 0.3, w: rw - 0.46, h: 0.55, fontSize: 10.5, color: C.accent6 });
    });
    k.card(s, { x: rx, y: 4.35, w: rw, h: 0.65, fill: C.accent1, name: "freeze" });
    k.text(s, [
      { text: "결정: 동결", options: { bold: true, breakLine: true } },
      { text: "화면과 코드는 유지, 추가 작업 중단", options: { fontSize: 10.5 } },
    ], { x: rx + 0.18, y: 4.38, w: rw - 0.36, h: 0.6, fontSize: 13, color: C.background1, valign: "middle" });
    s.addNotes(
      "분봉 모드는 직접 해 보니 일봉보다 재미가 덜했습니다. 왜 그런지 데이터로 확인해 봤습니다. 겹치지 않는 분봉 후보 여섯 개에서 가장 크게 떨어진 폭이 3.98% 였습니다. 분봉에서 고를 수 있는 가장 큰 배율 10의 청산선은 10% 하락이라서, 어떤 후보에서도 청산이 한 번도 나지 않습니다. " +
        "청산 위험이 없으면 배율은 그냥 수익을 키우는 버튼이 되고, 항상 최대 배율을 거는 게 정답이 돼서 고민이 사라집니다. 데이터를 더 받아 해결하기도 어려운 상황이라, 분봉 모드는 지우지 않고 그대로 둔 채 더 투자하지 않기로 했습니다."
    );
  }

  // ───────────────────────── 10. 피드백 반영 ─────────────────────────
  {
    const s = content("플레이로 내린 결정", "플레이하며 다듬은 것", "직접 해 보거나 함께 플레이하며 드러난 문제를 규칙과 설정으로 조정");
    const rows = [
      [I.clock, "틱이 너무 빠름", "테스트용 0.2초 틱으로 플레이", "기본 1틱 = 1초로 고정, 4분 한 판"],
      [I.timer, "판 길이가 하나뿐", "4분 고정", "방 만들 때 1분, 2분, 4분 중 선택"],
      [I.door, "나간 사람이 방에 남음", "대기 중 연결이 끊긴 참가자", "15초 뒤 자동 퇴장, 30분 무활동 대기방 정리"],
      [I.rotate, "새로고침하면 차트가 사라짐", "현재 틱만 다시 받음", "지금까지의 봉 전체 복원, 2초 뒤 자동 재연결"],
    ];
    const hdrY = 1.5;
    [["문제", M + 0.7], ["원인", M + 3.25], ["조정", M + 5.75]].forEach(([t, x]) => k.text(s, t, { x, y: hdrY, w: 2.2, h: 0.28, fontSize: 11, bold: true, color: C.accent6 }));
    rows.forEach(([ic, a, b, c], i) => {
      const y = 1.88 + i * 0.78;
      k.card(s, { x: M, y, w: W - 2 * M, h: 0.68, name: `fb-${i + 1}` });
      k.badge(s, ic, { x: M + 0.15, y: y + 0.12, d: 0.44 });
      k.text(s, a, { x: M + 0.7, y, w: 2.45, h: 0.68, fontSize: 12.5, bold: true, valign: "middle" });
      k.text(s, b, { x: M + 3.25, y, w: 2.4, h: 0.68, fontSize: 11, color: C.accent6, valign: "middle" });
      k.text(s, c, { x: M + 5.75, y, w: 3.15, h: 0.68, fontSize: 11.5, bold: true, color: C.accent1, valign: "middle" });
    });
    k.text(s, "한계: 1분, 2분 판은 시나리오 앞부분만 사용, 변동폭 기준은 4분 판 기준으로만 검사", { x: M, y: 4.98, w: W - 2 * M, h: 0.22, fontSize: 10, color: C.accent6 });
    s.addNotes(
      "플레이하면서 드러난 문제를 규칙과 설정으로 다듬었습니다. 처음 화면으로 해 봤을 때는 테스트용으로 0.2초 틱이라 너무 빨랐고, 기본을 1초로 맞췄습니다. 판 길이도 4분 하나뿐이어서 방을 만들 때 1분, 2분, 4분 중에 고르게 했습니다. " +
        "대기실에서 나간 사람이 방에 남는 문제는 연결이 끊기고 15초가 지나면 자동으로 내보내고, 30분 동안 아무 활동이 없는 방은 닫도록 했습니다. 새로고침하면 차트가 사라지던 문제는 지금까지의 봉을 모두 다시 받아 복원하게 고쳤습니다. " +
        "다만 짧은 판은 시나리오 앞부분만 쓰기 때문에, 변동폭 기준이 짧은 판에서도 지켜지는지는 아직 따로 검사하지 않습니다."
    );
  }

  // ───────────────────────── 11. 운영 사고와 복구 ─────────────────────────
  {
    const s = content("플레이로 내린 결정", "운영 사고와 복구", "개발 환경에서 생긴 사고 두 건, 원인과 재발 방지");
    const cases = [
      ["기기 이전 중 유실", "Windows PC에서 Mac으로 옮기며 원격 저장소에 올리지 않은 커밋 3개와 소스 일부 유실",
        [["복구", "남은 저장소 기록으로 재구성. 사라진 파일 5개 중 1개 원본 그대로, 3개 재작성, 1개 재수집 대기"], ["재발 방지", "작업 단위가 끝날 때마다 원격 저장소에 올림"]]],
      ["동기화 폴더 충돌", "iCloud와 동기화되는 바탕화면에 프로젝트를 두어 빌드 폴더에 충돌 복제본 20개 생성, 테스트 실행 실패",
        [["복구", "동기화되지 않는 폴더로 이전. 파일 850개, 내용, 기록 일치 확인 후 테스트 재통과"], ["재발 방지", "저장소와 빌드 결과는 동기화 폴더 밖에 둠"]]],
    ];
    const cw = 4.4, gap = 0.2;
    cases.forEach(([t, cause, rows], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 1.5, w: cw, h: 3.5, name: `case-${i + 1}` });
      k.text(s, t, { x: x + 0.25, y: 1.68, w: cw - 0.5, h: 0.32, fontSize: 16, bold: true, color: C.accent1 });
      k.text(s, cause, { x: x + 0.25, y: 2.05, w: cw - 0.5, h: 0.8, fontSize: 11.5 });
      rows.forEach(([h, d], j) => {
        const y = 2.95 + j * 1.0;
        k.card(s, { x: x + 0.2, y, w: cw - 0.4, h: 0.9, fill: C.background1, name: `case-${i + 1}-${j}` });
        k.text(s, h, { x: x + 0.35, y: y + 0.08, w: cw - 0.7, h: 0.26, fontSize: 11, bold: true, color: j === 0 ? C.accent5 : C.accent1 });
        k.text(s, d, { x: x + 0.35, y: y + 0.34, w: cw - 0.7, h: 0.52, fontSize: 10.5, color: C.accent6 });
      });
    });
    s.addNotes(
      "개발하면서 사고가 두 번 있었습니다. 하나는 개발 컴퓨터를 Windows에서 Mac으로 옮기면서, 아직 원격 저장소에 올리지 않은 커밋 세 개와 소스 일부를 잃은 일입니다. 남아 있던 저장소 기록으로 재구성했고, 사라진 파일 다섯 개 중 하나는 원본 그대로 되살렸습니다. 이후로는 작업 단위가 끝날 때마다 바로 올립니다. " +
        "다른 하나는 프로젝트를 iCloud와 동기화되는 바탕화면에 둬서 빌드 폴더에 충돌 복제본이 생기고 테스트가 깨진 일입니다. 동기화되지 않는 폴더로 옮기고, 파일과 기록이 모두 같은지 확인한 뒤 테스트를 다시 통과시켰습니다."
    );
  }

  // ───────────────────────── 12. 정리 ─────────────────────────
  pres.addSection({ title: "정리" });
  {
    const s = pres.addSlide({ masterName: "DARK_TOP", sectionTitle: "정리" });
    s.addText("정리", { placeholder: "kicker" });
    s.addText("만들고, 써 보고, 고침", { placeholder: "title" });
    const items = [
      ["플레이 가능한 게임 완성", "게임 화면, 결과 공개와 복기, 존버 봇 기준선"],
      ["여러 사람과 실제 플레이로 검증", "임시 공개로 외부 접속, 닉네임 도용 문제 발견과 보호"],
      ["플레이 결과를 데이터로 판단", "분봉 동결, 틱 속도와 판 길이, 대기방 정리, 재접속 복원"],
    ];
    items.forEach(([t, d], i) => {
      const y = 1.95 + i * 0.95;
      k.num(s, i + 1, { x: M, y, d: 0.45 });
      k.text(s, t, { x: M + 0.65, y: y - 0.04, w: 5.5, h: 0.34, fontSize: 17, bold: true, color: C.background1 });
      k.text(s, d, { x: M + 0.65, y: y + 0.33, w: 5.5, h: 0.3, fontSize: 12, color: C.background2 });
    });
    const nx = 6.6, nw = W - M - nx;
    k.card(s, { x: nx, y: 1.85, w: nw, h: 1.55, fill: "1B2233", name: "limit" });
    k.text(s, [
      { text: "현재 한계", options: { bold: true, fontSize: 13, color: HEX.accent4, breakLine: true } },
      { text: "겹치지 않는 일봉 판 2개\n밸런스 값 재검토 필요\n닉네임 보호는 브라우저 단위", options: { fontSize: 11, color: HEX.lt2 } },
    ], { x: nx + 0.2, y: 2.0, w: nw - 0.4, h: 1.3 });
    k.card(s, { x: nx, y: 3.6, w: nw, h: 1.3, fill: C.accent1, name: "message" });
    k.text(s, [
      { text: "한 줄 요약", options: { bold: true, fontSize: 12, breakLine: true } },
      { text: "만든 뒤 써 보고,\n데이터로 판단", options: { bold: true, fontSize: 18 } },
    ], { x: nx + 0.2, y: 3.72, w: nw - 0.4, h: 1.1, color: C.background1 });
    s.addNotes(
      "정리하겠습니다. 화면과 결과 공개, 존버 봇까지 붙여서 혼자서도 여럿이서도 플레이할 수 있는 게임을 만들었습니다. 다른 사람들과 실제로 플레이하면서 닉네임 문제를 발견해 고쳤고, 플레이 결과를 데이터로 확인해서 분봉 모드 동결 같은 결정을 내렸습니다. " +
        "아직 서로 겹치지 않는 일봉 판이 두 개뿐이라는 데이터 한계와, 밸런스 값 재검토가 남아 있습니다. 한 줄로 말씀드리면, 만든 뒤 직접 써 보고 데이터로 판단했습니다. 감사합니다."
    );
  }

  await finish(pres, OUT);
  console.log(`만들었습니다: ${OUT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
