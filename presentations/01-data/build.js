// 발표 — 실제 시세를 게임 재료로 만들기 (중간 점검, 15분 안팎, 12장)
// 범위: 기획 · 데이터 수집 · 시나리오. 구현 · 코드 이야기는 넣지 않는다 (「실측 숫자 → 판단」 수준).
// 근거는 CLAUDE.md §0 · §1 · §3 M1~M3 · §8 · §9-4 이다. 숫자를 바꾸려면 그쪽부터 확인한다.
//   NODE_PATH=<도구 폴더>/node_modules node presentations/01-data/build.js
const path = require("path");
const fa = require("react-icons/fa6");
const { HEX, W, M, newDeck, icon, kit, finish } = require("../common/deck-kit");

const OUT = path.join(__dirname, "moneygame-progress.pptx");

async function main() {
  const { pres, C } = newDeck({
    title: "머니게임 — 실제 시세를 게임 재료로 만들기",
    subject: "과거 시세 압축 재생 투자 게임 · 데이터 확보와 게임 구간 선정",
    footer: "머니게임 · 실제 시세를 게임 재료로",
  });
  const k = kit(pres, C);
  const I = {};
  for (const [key, comp] of Object.entries({
    users: fa.FaUsers, play: fa.FaForwardFast, lever: fa.FaArrowTrendUp, rank: fa.FaRankingStar,
    price: fa.FaScaleBalanced, calendar: fa.FaCalendarDays, pause: fa.FaPause, wave: fa.FaChartLine,
    shield: fa.FaShieldHalved, ban: fa.FaBan,
    gauge: fa.FaGaugeHigh, key: fa.FaKey, globe: fa.FaGlobe,
    filter: fa.FaFilter, tags: fa.FaTags, layers: fa.FaLayerGroup,
  })) {
    I[key] = await icon(comp);
  }

  const content = (section, title, lead) => {
    const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: section });
    s.addText(title, { placeholder: "title" });
    s.addText(lead, { placeholder: "lead" });
    return s;
  };

  // ───────────────────────── 1. 표지 ─────────────────────────
  pres.addSection({ title: "소개" });
  {
    const s = pres.addSlide({ masterName: "DARK", sectionTitle: "소개" });
    s.addText("머니게임 · 중간 점검", { placeholder: "kicker" });
    s.addText("실제 시세를\n게임 재료로 만들기", { placeholder: "title" });
    s.addText("과거 시세를 압축 재생해 겨루는 투자 게임\n데이터 확보와 게임 구간 선정", { placeholder: "body" });
    s.addText("", { placeholder: "presenter" }); // 이름은 PowerPoint 에서 채운다
    // 오른쪽 장식 — 오르는 캔들 (상승 빨강 · 하락 파랑)
    const candles = [
      [0, 3.6, 3.95, 3.45, 4.05], [1, 3.9, 3.55, 3.4, 4.0], [2, 3.55, 3.2, 3.1, 3.7], [3, 3.2, 3.35, 3.05, 3.45],
      [4, 3.35, 2.9, 2.8, 3.45], [5, 2.9, 2.55, 2.4, 3.0], [6, 2.55, 2.75, 2.45, 2.85], [7, 2.75, 2.3, 2.2, 2.85],
      [8, 2.3, 1.95, 1.8, 2.4], [9, 1.95, 2.15, 1.85, 2.25], [10, 2.15, 1.6, 1.45, 2.2],
    ]; // [i, 시가 y, 종가 y, 고가 y, 저가 y] — y 가 작을수록 높은 가격
    candles.forEach(([i, o, c, hi, lo]) => {
      const x = 7.0 + i * 0.22;
      const color = c < o ? C.accent2 : C.accent3;
      k.rect(s, { x: x + 0.065, y: hi, w: 0.02, h: lo - hi, fill: { color } });
      k.rect(s, { x, y: Math.min(o, c), w: 0.15, h: Math.max(Math.abs(o - c), 0.04), fill: { color } });
    });
    s.addNotes(
      "머니게임은 여러 명이 같은 방에서 실제 과거 시세를 빠르게 재생하며 거래하고, 끝날 때 남은 자산으로 순위를 가리는 웹게임입니다. " +
        "오늘은 이 게임의 재료가 되는 시세 데이터를 어떻게 확보하고, 게임에 쓸 구간을 어떤 기준으로 골랐는지 보고드리겠습니다."
    );
  }

  // ───────────────────────── 2. 어떤 게임인가 ─────────────────────────
  {
    const s = content("소개", "어떤 게임인가요", "같은 과거 시세를 빠르게 재생하며 거래하고, 끝날 때 남은 자산으로 순위를 가립니다");
    const steps = [
      [I.users, "방에 모인다", "여러 명이 한 방에서,\n혼자면 봇과 겨룬다"],
      [I.play, "시세 재생", "실제 과거 시세를\n1초에 캔들 하나씩"],
      [I.lever, "배율 걸고 거래", "손익이 커지는 만큼\n청산 위험도 커진다"],
      [I.rank, "자산으로 순위", "끝나면 실제 종목과\n기간을 공개한다"],
    ];
    const cw = 2.0, gap = 0.333, y = 1.55;
    steps.forEach(([ic, t, d], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y, w: cw, h: 2.15, name: `step-${i + 1}` });
      k.badge(s, ic, { x: x + 0.2, y: y + 0.22, d: 0.55 });
      k.text(s, `${i + 1}. ${t}`, { x: x + 0.2, y: y + 0.95, w: cw - 0.4, h: 0.35, fontSize: 15, bold: true });
      k.text(s, d, { x: x + 0.2, y: y + 1.35, w: cw - 0.4, h: 0.7, fontSize: 12, color: C.accent6 });
      if (i < steps.length - 1) k.arrow(s, x + cw + 0.05, y + 1.07, x + cw + gap - 0.05, y + 1.07);
    });
    const stats = [["1초", "= 캔들 1개. 과거 시세를 압축 재생"], ["4분", "한 판의 길이 (캔들 240개)"], ["A · B · C", "게임 중에는 종목 이름을 가린다"]];
    stats.forEach(([v, l], i) => {
      const x = M + i * 3.0;
      k.text(s, v, { x, y: 4.0, w: 2.8, h: 0.6, fontSize: 32, bold: true, color: C.accent1 });
      k.text(s, l, { x, y: 4.62, w: 2.8, h: 0.3, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "한 판의 흐름입니다. 방에 모여 시작하면 실제 과거 시세를 1초에 캔들 하나씩 재생합니다. 종목 이름은 가리고 A, B, C 로만 보여줍니다. " +
        "배율을 걸면 손익이 커지지만 손실이 커지면 강제로 청산됩니다. 4분이 지나면 남은 자산으로 순위를 매기고 실제 종목을 공개합니다. " +
        "그래서 이 게임은 실제 시세 데이터 없이는 시작할 수 없습니다."
    );
  }

  // ───────────────────────── 3. 데이터의 조건 ─────────────────────────
  {
    const s = content("소개", "게임에 쓸 데이터의 조건", "실제 시세를 게임에 쓰려면 네 가지가 필요했습니다. 이후 내용은 이것을 하나씩 확인한 과정입니다");
    const conds = [
      [I.price, "진짜 가격이어야 한다", "액면분할 같은 사건이 반영된 수정주가\n삼성전자 분할(50:1) 전후 정확히 50배 차이", "확인 완료"],
      [I.calendar, "고를 구간이 충분해야 한다", "같은 판이 반복되지 않으려면\n받을 수 있는 기간이 길어야 한다", "→ 조회 범위 실측"],
      [I.pause, "멈추지 않아야 한다", "1초에 캔들 하나라, 거래 없는 봉은\n화면이 멈춘 것처럼 보인다", "→ 거래량 0 봉 분석"],
      [I.wave, "움직임이 있어야 한다", "가격이 잔잔하면 판단할 거리가 없다.\n배율을 걸어도 위험이 생기지 않는다", "→ 변동폭 · 청산 분석"],
    ];
    const cw = 4.4, ch = 1.6;
    conds.forEach(([ic, t, d, tag], i) => {
      const x = M + (i % 2) * (cw + 0.2);
      const y = 1.5 + Math.floor(i / 2) * (ch + 0.2);
      k.card(s, { x, y, w: cw, h: ch, name: `cond-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: y + 0.22, d: 0.5 });
      k.text(s, t, { x: x + 0.9, y: y + 0.22, w: cw - 1.1, h: 0.5, fontSize: 16, bold: true, valign: "middle" });
      k.text(s, tag, { x: x + cw - 2.2, y: y + ch - 0.36, w: 2.0, h: 0.26, fontSize: 11, bold: true, color: i === 0 ? C.accent5 : C.accent1, align: "right", valign: "middle" });
      k.text(s, d, { x: x + 0.22, y: y + 0.8, w: cw - 0.44, h: 0.5, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "실제 시세를 게임에 쓰려면 네 가지 조건이 필요했습니다. 첫째, 분할 같은 사건이 반영된 진짜 가격이어야 합니다. 이건 삼성전자 액면분할 전후가 정확히 50배 차이 나는 것으로 확인했습니다. " +
        "둘째, 같은 판이 반복되지 않으려면 고를 구간이 많아야 합니다. 셋째, 1초에 캔들 하나씩 재생하니 거래가 없는 구간은 화면이 멈춘 것처럼 보입니다. 넷째, 가격이 너무 잔잔하면 판단할 거리가 없습니다. " +
        "이제부터는 이 조건들을 실제 데이터로 하나씩 확인한 과정입니다."
    );
  }

  // ───────────────────────── 4. 데이터 흐름과 원칙 ─────────────────────────
  pres.addSection({ title: "데이터 확보" });
  {
    const s = content("데이터 확보", "데이터는 미리 받아 두고, 게임은 저장된 것만 쓴다", "게임이 도는 동안에는 외부 API 를 부르지 않습니다");
    const box = (x, w, title, sub, fill, color = C.text1, subColor = C.accent6) => {
      k.card(s, { x, y: 1.55, w, h: 1.1, fill, name: title });
      k.text(s, title, { x: x + 0.1, y: 1.72, w: w - 0.2, h: 0.35, fontSize: 15, bold: true, align: "center", color });
      k.text(s, sub, { x: x + 0.1, y: 2.08, w: w - 0.2, h: 0.5, fontSize: 11, align: "center", color: subColor });
    };
    box(M, 1.9, "토스증권 API", "실제 시세 (일봉 · 분봉)", C.background2);
    box(2.85, 1.9, "수집", "필요할 때만 실행", C.background2);
    box(5.2, 1.9, "데이터베이스", "시세 · 게임 구간", C.accent1, C.background1, C.background2);
    box(7.55, 1.95, "게임", "판 시작 때 한 번 읽기", C.background2);
    k.arrow(s, 2.42, 2.1, 2.8, 2.1);
    k.arrow(s, 4.77, 2.1, 5.15, 2.1);
    k.arrow(s, 7.12, 2.1, 7.5, 2.1);
    const rules = [
      [I.shield, "시세 조회만 쓴다", "이 API 는 실계좌 주문까지 열린다.\n주문 · 계좌 · 잔고 기능은 어떤 경우에도 부르지 않는다"],
      [I.ban, "게임 중에는 바깥을 부르지 않는다", "판 시작 때 시세를 한 번 읽고 진행은 서버 안에서만.\n네트워크 · DB 지연이 게임 진행에 끼어들지 않는다"],
    ];
    rules.forEach(([ic, t, d], i) => {
      const x = M + i * 4.6;
      k.card(s, { x, y: 3.0, w: 4.4, h: 1.95, name: `principle-${i + 1}` });
      k.badge(s, ic, { x: x + 0.25, y: 3.22, d: 0.55 });
      k.text(s, t, { x: x + 0.95, y: 3.22, w: 3.3, h: 0.55, fontSize: 16, bold: true, valign: "middle" });
      k.text(s, d, { x: x + 0.25, y: 3.98, w: 3.9, h: 0.85, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "데이터는 미리 받아 두고, 게임은 저장된 데이터만 씁니다. 토스증권 Open API 에서 필요할 때만 받아 데이터베이스에 쌓고, 게임은 판이 시작될 때 한 번 읽습니다. " +
        "원칙이 두 가지 있습니다. 이 API 는 실계좌 주문까지 가능해서 시세 조회 외의 기능은 절대 부르지 않습니다. 그리고 게임이 진행되는 동안에는 바깥을 부르지 않아서, 네트워크 지연이 게임에 영향을 주지 않게 했습니다."
    );
  }

  // ───────────────────────── 5. 실측 ① 조회 범위 ─────────────────────────
  {
    const s = content("데이터 확보", "실측 ① 얼마나 오래된 시세까지 받을 수 있나", "문서에 보존 기간이 없어서, 삼성전자(005930)를 끝까지 받아 봤습니다");
    const stat = (x, value, label, detail) => {
      k.text(s, value, { x, y: 1.5, w: 4.2, h: 0.85, fontSize: 48, bold: true, color: C.accent1 });
      k.text(s, label, { x, y: 2.35, w: 4.2, h: 0.32, fontSize: 16, bold: true });
      k.text(s, detail, { x, y: 2.68, w: 4.2, h: 0.32, fontSize: 12, color: C.accent6 });
    };
    stat(M, "51년 3개월", "일봉 — 1975-06-12부터", "13,735봉 · 마지막 페이지까지 확인 (경계 확정)");
    stat(5.2, "4년 5개월+", "분봉 — 2022-04-19 이전부터", "775,000봉+ · 3,000번 요청해도 끝 미도달");
    const x0 = M, x1 = W - M, y0 = 3.3, t0 = 1975.45, t1 = 2026.7;
    const xAt = (t) => x0 + ((t - t0) / (t1 - t0)) * (x1 - x0);
    k.rect(s, { x: xAt(1975.45), y: y0, w: xAt(2026.7) - xAt(1975.45), h: 0.22, fill: { color: C.accent1 } });
    k.rect(s, { x: xAt(2022.3), y: y0 + 0.32, w: xAt(2026.7) - xAt(2022.3), h: 0.22, fill: { color: C.accent4 } });
    k.text(s, "일봉 1975 ~", { x: x0 + 0.1, y: y0, w: 2, h: 0.22, fontSize: 10, bold: true, color: C.background1, valign: "middle" });
    k.text(s, "분봉 2022 ~", { x: xAt(2022.3) - 1.05, y: y0 + 0.32, w: 1.0, h: 0.22, fontSize: 10, bold: true, color: C.accent4, align: "right", valign: "middle" });
    [1975, 1990, 2000, 2010, 2020, 2026].forEach((yr) => {
      k.text(s, String(yr), { x: xAt(yr) - 0.3, y: y0 + 0.62, w: 0.6, h: 0.22, fontSize: 10, color: C.accent6, align: "center" });
    });
    k.card(s, { x: M, y: 4.25, w: W - 2 * M, h: 0.75, fill: C.background2, name: "takeaway" });
    k.text(s, [
      { text: "「1분봉은 최근 몇 주치만 남는다」는 통념은 틀렸다", options: { bold: true, breakLine: true } },
      { text: "→ 서둘러 쌓을 필요가 없다. 분봉 4년치는 요청 3,000번 · 68분이라, 게임에 쓸 구간만 골라 받으면 된다", options: { fontSize: 12, color: C.accent6 } },
    ], { x: M + 0.25, y: 4.33, w: W - 2 * M - 0.5, h: 0.62, fontSize: 14 });
    s.addNotes(
      "API 문서에는 데이터를 얼마나 오래 보관하는지 나와 있지 않아서 직접 끝까지 받아 봤습니다. 일봉은 1975년까지 51년 넘게 나오고 마지막 페이지로 경계도 확인했습니다. " +
        "분봉은 3천 번을 요청해도 끝에 닿지 않아 최소 4년 5개월입니다. 1분봉은 최근 몇 주치만 남는다는 통념과 달랐고, 덕분에 데이터를 서둘러 쌓지 않고 필요한 구간만 받기로 했습니다."
    );
  }

  // ───────────────────────── 6. 실측 ② 함정 ─────────────────────────
  {
    const s = content("데이터 확보", "실측 ② 수집을 멈춰 세운 문제 세 가지", "문서만 봐서는 알 수 없었고, 실제로 막힌 뒤에 원인을 찾았습니다");
    const traps = [
      [I.gauge, "267", "번 연속 요청에서 차단", "평균 속도가 느려도 연속으로\n몰아서 보내면 막히는 방식\n(쌓아 둔 횟수를 쓰는 토큰 버킷)", "요청 사이에 0.3초 →\n1,800번 연속 무사고"],
      [I.key, "608", "번째 요청에서 인증 무효", "인증 토큰은 하나만 유효.\n수집을 두 개 동시에 돌리자\n서로의 토큰을 무효로 만들었다", "토큰을 재사용하고,\n수집은 한 번에 하나만"],
      [I.globe, "403", "허용되지 않은 IP", "등록한 IP 에서만 쓸 수 있다.\n개발 컴퓨터를 바꾸자\n인증부터 막혔다", "장소를 옮기면\nIP 부터 등록"],
    ];
    const cw = 2.85, gap = 0.225;
    traps.forEach(([ic, n, unit, cause, fix], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 1.5, w: cw, h: 3.5, name: `trap-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: 1.7, d: 0.45 });
      k.text(s, n, { x: x + 0.22, y: 2.2, w: cw - 0.44, h: 0.65, fontSize: 40, bold: true, color: C.accent1 });
      k.text(s, unit, { x: x + 0.22, y: 2.85, w: cw - 0.44, h: 0.3, fontSize: 13, bold: true });
      k.text(s, cause, { x: x + 0.22, y: 3.22, w: cw - 0.44, h: 0.85, fontSize: 11, color: C.accent6 });
      k.text(s, fix, { x: x + 0.22, y: 4.15, w: cw - 0.44, h: 0.7, fontSize: 12, bold: true, color: C.text1 });
    });
    s.addNotes(
      "수집을 실제로 멈춰 세운 문제가 세 가지 있었습니다. 첫째, 속도 제한이 평균이 아니라 연속 요청을 막는 방식이어서, 요청 사이에 0.3초를 두는 것으로 해결했습니다. " +
        "둘째, 인증 토큰은 하나만 살아 있어서 수집을 두 개 동시에 돌리면 서로 무효로 만듭니다. 셋째, 등록된 IP 에서만 쓸 수 있어서 개발 컴퓨터를 바꾸자마자 막혔습니다. " +
        "세 가지 모두 원인을 찾아 해결했고, 운영 수칙으로 정리해 두었습니다."
    );
  }

  // ───────────────────────── 7. 확보한 데이터 ─────────────────────────
  {
    const s = content("데이터 확보", "지금 확보한 데이터", "실측은 전 구간으로 했고, 개발에는 필요한 구간만 받아 두었습니다");
    const stocks = [["삼성전자", "005930"], ["SK하이닉스", "000660"], ["에코프로비엠", "247540"]];
    k.text(s, "종목 3개 — 1주 가격이 수만 원부터 백만 원대까지 다르다", { x: M, y: 1.5, w: 4.3, h: 0.3, fontSize: 13, bold: true });
    stocks.forEach(([name, code], i) => {
      const y = 1.95 + i * 0.95;
      k.card(s, { x: M, y, w: 4.2, h: 0.8, name: `stock-${i + 1}` });
      k.text(s, name, { x: M + 0.25, y, w: 2.6, h: 0.8, fontSize: 17, bold: true, valign: "middle" });
      k.text(s, code, { x: M + 2.8, y, w: 1.2, h: 0.8, fontSize: 12, color: C.accent6, align: "right", valign: "middle" });
    });
    const rx = 5.1, rw = W - M - rx;
    const blocks = [
      ["일봉", "658봉 × 3종목", "2024-01-02 ~ 2026-09-14 · 종목당 약 2년 8개월"],
      ["분봉", "14일 × 3종목", "2026-08-25 ~ 09-14 중 정규장이 빠짐없는 날"],
    ];
    blocks.forEach(([tag, v, d], i) => {
      const y = 1.5 + i * 1.45;
      k.card(s, { x: rx, y, w: rw, h: 1.3, fill: C.background2, name: `data-${tag}` });
      k.text(s, tag, { x: rx + 0.25, y: y + 0.15, w: 1, h: 0.3, fontSize: 12, bold: true, color: C.accent1 });
      k.text(s, v, { x: rx + 0.25, y: y + 0.42, w: rw - 0.5, h: 0.5, fontSize: 24, bold: true });
      k.text(s, d, { x: rx + 0.25, y: y + 0.92, w: rw - 0.5, h: 0.3, fontSize: 11, color: C.accent6 });
    });
    k.text(s, [
      { text: "같은 수집을 다시 돌려도 중복 없이,", options: { bold: true, breakLine: true } },
      { text: "빠진 구간만 이어 받는다", options: { bold: true, color: C.accent1 } },
    ], { x: rx, y: 4.4, w: rw, h: 0.6, fontSize: 13, valign: "middle" });
    s.addNotes(
      "실측은 전 구간을 대상으로 했지만, 개발에는 필요한 만큼만 받아 두었습니다. 1주 가격대가 수만 원부터 백만 원대까지 서로 다른 세 종목을 골랐고, " +
        "일봉은 2024년부터 약 2년 8개월, 분봉은 정규장이 온전한 14일치입니다. 수집은 몇 번을 다시 돌려도 중복 없이 빠진 구간만 이어 받도록 만들었습니다."
    );
  }

  // ───────────────────────── 8. 시나리오 ─────────────────────────
  pres.addSection({ title: "게임 구간 선정" });
  {
    const s = content("게임 구간 선정", "게임 한 판 = 시나리오", "같은 시각의 캔들 241개를 종목별로 묶은 것. 운영자가 미리 골라 둡니다");
    ["A", "B", "C"].forEach((l, i) => {
      const y = 1.6 + i * 0.72;
      k.num(s, l, { x: M, y, d: 0.5, fill: C.text1 });
      k.rect(s, { x: M + 0.7, y: y + 0.08, w: 0.45, h: 0.34, fill: { color: C.accent4 } });
      k.rect(s, { x: M + 1.2, y: y + 0.08, w: 4.0, h: 0.34, fill: { color: C.accent1 } });
      if (i === 0) {
        k.text(s, "시작가", { x: M + 0.7, y: y + 0.08, w: 0.45, h: 0.34, fontSize: 9, bold: true, align: "center", valign: "middle" });
        k.text(s, "240틱 — 1초에 1봉", { x: M + 1.2, y: y + 0.08, w: 4.0, h: 0.34, fontSize: 11, bold: true, color: C.background1, align: "center", valign: "middle" });
      }
    });
    k.text(s, "시작가 1봉 + 240봉 = 241봉. 모든 종목의 i 번째 봉은 같은 시각", { x: M, y: 3.78, w: 5.2, h: 0.3, fontSize: 12, color: C.accent6 });
    const stats = [["378", "일봉 적격 구간"], ["160", "분봉 적격 구간"]];
    stats.forEach(([v, l], i) => {
      const x = M + i * 2.65;
      k.card(s, { x, y: 4.2, w: 2.55, h: 0.8, fill: C.background2, name: `cand-${i + 1}` });
      k.text(s, v, { x: x + 0.2, y: 4.2, w: 1.0, h: 0.8, fontSize: 26, bold: true, color: C.accent1, valign: "middle" });
      k.text(s, l, { x: x + 1.15, y: 4.2, w: 1.3, h: 0.8, fontSize: 12, valign: "middle" });
    });
    const rules = [
      [I.tags, "종목 이름은 라벨로 가린다", "A · B · C 는 등록할 때 무작위로 붙인다.\n매번 A 가 같은 종목이면 가리는 의미가 없다"],
      [I.layers, "날짜가 어긋나면 쓰지 않는다", "한 종목만 거래정지로 봉이 빠지면\n틱마다 종목별 날짜가 달라진다"],
      [I.filter, "후보는 자동, 선택은 사람", "기준을 통과한 구간을 변동폭 순으로 추리고,\n어떤 판을 쓸지는 사람이 고른다"],
    ];
    rules.forEach(([ic, t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.badge(s, ic, { x: 5.95, y, d: 0.48 });
      k.text(s, t, { x: 6.58, y: y - 0.02, w: W - M - 6.58, h: 0.32, fontSize: 14, bold: true });
      k.text(s, d, { x: 6.58, y: y + 0.3, w: W - M - 6.58, h: 0.75, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "게임 한 판에 쓰는 시세 묶음을 시나리오라고 부릅니다. 종목마다 시작가 1봉과 240틱, 모두 241봉이고 모든 종목의 같은 순번 봉은 같은 시각입니다. " +
        "종목 이름은 라벨로 가리고, 날짜가 어긋나는 구간은 쓰지 않습니다. 기준을 통과한 후보는 자동으로 추리되, 실제로 쓸 판은 사람이 고릅니다. " +
        "지금 데이터에서 기준을 통과한 구간은 일봉 378개, 분봉 160개입니다. 그 기준이 다음 장들입니다."
    );
  }

  // ───────────────────────── 9. 분봉 09:01 ~ 15:20 ─────────────────────────
  {
    const s = content("게임 구간 선정", "분봉은 09:01 ~ 15:20 만 쓴다", "API 분봉은 시간외까지 하루 720봉. 시간외와 경계 봉에는 「거래량 0」 봉이 몰려 있습니다");
    const x0 = M, x1 = 6.0, y = 1.75, h = 0.5;
    const t2x = (hh, mm = 0) => x0 + ((hh + mm / 60 - 8) / 12) * (x1 - x0);
    const segs = [
      [8, 1, 8, 59, C.accent6, 60, ""],
      [8, 59, 9, 0, C.accent2, 0, ""],
      [9, 1, 15, 20, C.accent1, 0, "정규장 09:01 ~ 15:20"],
      [15, 20, 15, 30, C.accent2, 0, ""],
      [15, 30, 20, 0, C.accent6, 60, "애프터"],
    ];
    segs.forEach(([h1, m1, h2, m2, color, tr, label], i) => {
      const xa = t2x(h1, m1), xb = t2x(h2, m2);
      k.rect(s, { x: xa, y, w: Math.max(xb - xa, 0.05), h, fill: { color, transparency: tr }, objectName: `seg-${i}` });
      if (label) k.text(s, label, { x: xa, y, w: xb - xa, h, fontSize: 11, bold: true, align: "center", valign: "middle", color: i === 2 ? C.background1 : C.text1 });
    });
    k.text(s, "09:00 봉", { x: t2x(9) - 0.45, y: y - 0.27, w: 0.9, h: 0.22, fontSize: 9, bold: true, color: C.accent2, align: "center" });
    k.text(s, "15:21 ~ 15:30", { x: t2x(15, 25) - 0.55, y: y - 0.27, w: 1.1, h: 0.22, fontSize: 9, bold: true, color: C.accent2, align: "center" });
    [8, 10, 12, 14, 16, 18, 20].forEach((hh) => k.text(s, `${hh}시`, { x: t2x(hh) - 0.3, y: y + h + 0.05, w: 0.6, h: 0.22, fontSize: 9, color: C.accent6, align: "center" }));
    const rows = [
      ["프리마켓 08시", "거래량 0 봉 15.3%", C.text1],
      ["09:00 봉 = 개장 전 1분 [08:59, 09:00)", "14일 모두 거래량 0", C.accent2],
      ["15:21 ~ 15:30 종가 단일가", "14일 모두 거래량 0", C.accent2],
      ["애프터 16 ~ 19시", "거래량이 정규장의 약 1/8", C.text1],
      ["정규장", "거래량 0 봉 0 ~ 1.7%", C.accent1],
    ];
    rows.forEach(([a, b, color], i) => {
      const yy = 2.75 + i * 0.42;
      k.text(s, a, { x: x0, y: yy, w: 3.2, h: 0.32, fontSize: 12 });
      k.text(s, b, { x: x0 + 3.2, y: yy, w: x1 - x0 - 3.2, h: 0.32, fontSize: 12, bold: true, color, align: "right" });
      k.line(s, { x: x0, y: yy + 0.37, w: x1 - x0, h: 0, line: { color: C.background2, width: 0.75 } });
    });
    const sx = 6.5, sw = W - M - sx;
    k.card(s, { x: sx, y: 1.5, w: sw, h: 3.5, fill: C.background2, name: "result" });
    k.text(s, "두 경계 봉을 빼면", { x: sx + 0.25, y: 1.68, w: sw - 0.5, h: 0.3, fontSize: 13, bold: true });
    const big = (yy, from, to, label) => {
      k.text(s, [
        { text: from, options: { color: C.accent6 } },
        { text: " → ", options: { color: C.accent6 } },
        { text: to, options: { color: C.accent1 } },
      ], { x: sx + 0.25, y: yy, w: sw - 0.5, h: 0.6, fontSize: 24, bold: true, valign: "middle" });
      k.text(s, label, { x: sx + 0.25, y: yy + 0.6, w: sw - 0.5, h: 0.28, fontSize: 11, color: C.accent6 });
    };
    big(2.08, "2.8%", "0.3%", "거래량 0 봉 비율");
    big(3.05, "11봉", "1봉", "최대 연속 정지 (1틱 = 1초라 11초 정지)");
    k.text(s, "하루 380봉 → 판 시작점 140개\n날짜는 넘기지 않는다 (밤사이 갭)", { x: sx + 0.25, y: 4.12, w: sw - 0.5, h: 0.75, fontSize: 11 });
    s.addNotes(
      "분봉은 대체거래소까지 합쳐 하루 720봉이지만 전부 쓰면 안 됩니다. 시간대별로 재 보니 프리마켓에는 거래량 0 봉이 15% 섞여 있고, 애프터는 거래가 정규장의 8분의 1 수준입니다. " +
        "특히 9시 봉과 15시 21분부터 30분까지는 14일 내내 거래량이 0 이었습니다. 9시 봉은 사실 개장 전 1분이고, 15시 21분부터는 종가 단일가 구간이기 때문입니다. " +
        "이 두 경계를 빼면 거래량 0 봉이 2.8% 에서 0.3% 로, 최대 연속 정지가 11봉에서 1봉으로 줄어듭니다. 11봉 정지는 화면이 11초 멈추는 것과 같습니다."
    );
  }

  // ───────────────────────── 10. 적격성 검사 ─────────────────────────
  {
    const s = content("게임 구간 선정", "적격성 검사 — 멈추거나 밋밋한 구간은 버린다", "하나라도 넘으면 게임에 쓰지 않습니다. 기준값은 플레이해 보며 조정할 수 있게 열어 두었습니다");
    const checks = [
      ["거래량 0 봉 비율", "≤ 5%", "정상 구간 실측은 0.3%. 여유 있게 통과한다"],
      ["연속 정지", "≤ 5봉", "시간외 섞인 구간: 비율 4.6% 로 통과,\n연속 11봉에서 걸린다 — 비율만으론 못 거른다"],
      ["최소 이동폭", "분봉 2.5% · 일봉 30%", "시작가 대비 가장 크게 움직인 폭. 너무 잔잔한 판은 버린다"],
    ];
    checks.forEach(([t, v, d], i) => {
      const y = 1.5 + i * 1.17;
      k.card(s, { x: M, y, w: 4.5, h: 1.02, name: `check-${i + 1}` });
      k.text(s, t, { x: M + 0.22, y: y + 0.14, w: 2.0, h: 0.3, fontSize: 14, bold: true });
      k.text(s, v, { x: M + 2.1, y: y + 0.1, w: 2.2, h: 0.38, fontSize: 17, bold: true, color: C.accent1, align: "right" });
      k.text(s, d, { x: M + 0.22, y: y + 0.52, w: 4.06, h: 0.45, fontSize: 11, color: C.accent6 });
    });
    const tx = 5.3, tw = W - M - tx;
    k.text(s, "시작가 대비 최대 이동폭 — 240봉 구간 분포", { x: tx, y: 1.5, w: tw, h: 0.3, fontSize: 13, bold: true });
    const hdr = (t) => ({ text: t, options: { bold: true, color: HEX.lt1, fill: { color: HEX.accent1 }, align: "center" } });
    const cell = (t, o = {}) => ({ text: t, options: { align: "center", ...o } });
    s.addTable(
      [
        [hdr("백분위"), hdr("분봉"), hdr("일봉")],
        [cell("10%"), cell("0.99%"), cell("22.59%")],
        [cell("25%"), cell("1.38%"), cell("29.63%")],
        [cell("50% (중앙)", { bold: true }), cell("1.95%", { bold: true }), cell("46.27%", { bold: true })],
        [cell("75%"), cell("2.81%"), cell("89.66%")],
      ],
      { x: tx, y: 1.9, w: tw, colW: [tw * 0.36, tw * 0.32, tw * 0.32], rowH: 0.36, fontSize: 12, color: HEX.dk1, border: { type: "solid", pt: 0.75, color: "D9DCE3" }, valign: "middle" }
    );
    k.text(s, [
      { text: "스케일이 20배 넘게 달라 기준을 단위별로 따로 둔다.", options: { bold: true, breakLine: true } },
      { text: "분봉 하한을 25%ile(1.38%)이 아니라 2.5% 로 올렸다 — 1.5% 면 배율 10 을 걸어도 자산 변동이 15% 뿐이라 4분짜리 판이 밋밋하다", options: { color: C.accent6 } },
    ], { x: tx, y: 3.85, w: tw, h: 1.15, fontSize: 11 });
    s.addNotes(
      "그래서 게임에 쓰기 전에 적격성 검사를 합니다. 거래량 0 봉 비율은 5% 이하, 연속으로 멈추는 건 5봉 이하여야 합니다. 시간외가 섞인 구간은 비율로는 통과하지만 연속 검사에서 걸리기 때문에 두 검사가 모두 필요합니다. " +
        "변동성 하한은 분봉과 일봉을 따로 둡니다. 오른쪽 표처럼 중앙값이 분봉 2%, 일봉 46% 로 스케일이 완전히 다르기 때문입니다. 기준값은 플레이해 보며 조정할 수 있게 열어 두었습니다."
    );
  }

  // ───────────────────────── 11. 배율별 청산 ─────────────────────────
  {
    const s = content("게임 구간 선정", "실측이 바꾼 결정 — 밸런스는 시나리오로 맞춘다", "240봉 구간을 한 봉씩 밀어 가며 전수 계산 — 청산이 한 번이라도 나는 구간의 비율");
    // 막대그래프 — 도형으로 그린다. pptxgenjs 차트는 Keynote 가 그리지 못한다 (PowerPoint · Keynote · 구글 슬라이드 공통으로 보이게)
    const levers = ["×3", "×5", "×10", "×20", "×30"];
    const daily = [13.04, 37.82, 60.18, 73.31, 79.69];
    const minute = [0, 0, 0, 0, 5.44];
    const gx = M + 0.1, gw = 5.3, base = 4.45, maxH = 2.45;
    k.text(s, "배율별 청산 발생 구간 비율", { x: M, y: 1.45, w: 3.2, h: 0.3, fontSize: 13, bold: true });
    [[C.accent1, "일봉 6,878구간"], [C.accent4, "분봉 5,922구간"]].forEach(([color, label], i) => {
      const lx = M + 3.25 + i * 1.15;
      k.rect(s, { x: lx, y: 1.55, w: 0.14, h: 0.14, fill: { color } });
      k.text(s, label, { x: lx + 0.2, y: 1.48, w: 0.95, h: 0.28, fontSize: 9, color: C.accent6, valign: "middle" });
    });
    k.line(s, { x: gx, y: base, w: gw, h: 0, line: { color: C.accent6, width: 1 } });
    const slot = gw / levers.length, bw = 0.34;
    levers.forEach((lv, i) => {
      const cx = gx + slot * i + slot / 2;
      [[daily[i], C.accent1, -bw - 0.02], [minute[i], C.accent4, 0.02]].forEach(([v, color, dx]) => {
        const hh = Math.max((v / 100) * maxH, 0.02);
        k.rect(s, { x: cx + dx, y: base - hh, w: bw, h: hh, fill: { color }, objectName: `bar-${lv}-${color}` });
        k.text(s, v ? `${v.toFixed(1)}%` : "0%", { x: cx + dx - 0.15, y: base - hh - 0.26, w: bw + 0.3, h: 0.24, fontSize: 10, bold: true, align: "center", color: C.text1 });
      });
      k.text(s, lv, { x: cx - 0.4, y: base + 0.08, w: 0.8, h: 0.26, fontSize: 12, bold: true, align: "center", color: C.accent6 });
    });
    k.text(s, "청산이 한 번이라도 나는 240봉 구간의 비율 (2026-09-14 실측)", { x: M, y: 4.8, w: 5.4, h: 0.24, fontSize: 9, color: C.accent6 });
    const tx = 6.2, tw = W - M - tx;
    const points = [
      ["분봉은 ×20 까지 청산 0%", "하루 시가 → 저가 −10% 이하는\n169일에 한 번. 배율을 올려도\n「망할 위험」이 생기지 않는다"],
      ["종목 차이가 배율 차이보다 크다", "−5% 기준 에코프로비엠 11.89%\nvs 삼성전자 1.60% — 7배"],
      ["그래서 시나리오를 고른다", "평범한 날 대신 변동이 큰 구간을\n고른다 → 적격성 검사의 이동폭 하한"],
    ];
    points.forEach(([t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.num(s, i + 1, { x: tx, y, d: 0.36 });
      k.text(s, t, { x: tx + 0.5, y: y - 0.01, w: tw - 0.5, h: 0.32, fontSize: 14, bold: true });
      k.text(s, d, { x: tx + 0.5, y: y + 0.33, w: tw - 0.5, h: 0.75, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "배율 상한을 어떻게 정할지 고민하다가 직접 계산해 봤습니다. 240봉 구간을 한 봉씩 밀어 가며, 배율별로 청산이 한 번이라도 나는 구간이 얼마나 되는지 셌습니다. " +
        "일봉은 배율 3 에서도 13% 구간에서 청산이 나지만, 분봉은 배율 20 까지도 0% 였습니다. 하루 안에서 10% 넘게 빠지는 날 자체가 169일에 한 번뿐이기 때문입니다. " +
        "또 같은 조건에서 종목 간 차이가 7배나 났습니다. 그래서 밸런스는 배율이 아니라 변동이 큰 구간을 골라서 맞추기로 했고, 그게 앞 장의 이동폭 기준입니다."
    );
  }

  // ───────────────────────── 12. 정리 ─────────────────────────
  pres.addSection({ title: "정리" });
  {
    const s = pres.addSlide({ masterName: "DARK_TOP", sectionTitle: "정리" });
    s.addText("정리", { placeholder: "kicker" });
    s.addText("지금까지 확인한 것", { placeholder: "title" });
    const items = [
      ["데이터 조건 네 가지를 실측으로 확인했다", "수정주가 · 조회 범위 · 거래량 0 봉 · 변동폭"],
      ["수집을 막은 문제 세 가지를 해결했다", "연속 요청 차단 · 토큰 무효 · IP 제한"],
      ["게임 구간을 고르는 기준을 숫자로 정했다", "정규장 09:01 ~ 15:20 · 적격성 검사 · 배율별 청산"],
    ];
    items.forEach(([t, d], i) => {
      const y = 1.95 + i * 0.95;
      k.num(s, i + 1, { x: M, y, d: 0.45 });
      k.text(s, t, { x: M + 0.65, y: y - 0.04, w: 5.4, h: 0.34, fontSize: 17, bold: true, color: C.background1 });
      k.text(s, d, { x: M + 0.65, y: y + 0.33, w: 5.4, h: 0.3, fontSize: 12, color: C.background2 });
    });
    const nx = 6.6, nw = W - M - nx;
    k.card(s, { x: nx, y: 1.85, w: nw, h: 1.55, fill: "1B2233", name: "limit" });
    k.text(s, [
      { text: "현재 한계", options: { bold: true, fontSize: 13, color: HEX.accent4, breakLine: true } },
      { text: "저장해 둔 데이터가 2년 8개월치\n3종목이라 겹치지 않는 판이\n일봉 2개 · 분봉 6개뿐", options: { fontSize: 11, color: HEX.lt2 } },
    ], { x: nx + 0.2, y: 2.0, w: nw - 0.4, h: 1.3 });
    k.card(s, { x: nx, y: 3.6, w: nw, h: 1.3, fill: C.accent1, name: "message" });
    k.text(s, [
      { text: "한 줄로", options: { bold: true, fontSize: 12, breakLine: true } },
      { text: "감이 아니라\n실측으로 정했다", options: { bold: true, fontSize: 18 } },
    ], { x: nx + 0.2, y: 3.72, w: nw - 0.4, h: 1.1, color: C.background1 });
    s.addNotes(
      "정리하겠습니다. 게임에 쓸 데이터의 조건 네 가지를 실제 데이터로 확인했고, 수집을 막은 문제 세 가지를 원인까지 찾아 해결했습니다. 그리고 게임에 쓸 구간을 고르는 기준을 숫자로 정했습니다. " +
        "현재 한계는 저장해 둔 데이터의 폭이 좁아서 서로 겹치지 않는 판의 수가 적다는 점입니다. 한 줄로 말씀드리면, 감이 아니라 실측으로 정했습니다. 감사합니다."
    );
  }

  await finish(pres, OUT);
  console.log(`만들었습니다: ${OUT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
