// 발표 1 — 실제 시세를 게임 재료로 만들기 (기획, S0~S1, M1 · M2 · M3)
// 근거는 CLAUDE.md §0 · §1 · §2 · §3 M1~M3 · §8 이다. 숫자를 바꾸려면 그쪽부터 확인한다.
//   NODE_PATH=<도구 폴더>/node_modules node presentations/01-data/build.js
const path = require("path");
const fa = require("react-icons/fa6");
const { HEX, W, M, newDeck, icon, kit, finish } = require("../common/deck-kit");

const OUT = path.join(__dirname, "moneygame-talk1-data.pptx");

async function main() {
  const { pres, C } = newDeck({
    title: "머니게임 발표 1 — 실제 시세를 게임 재료로 만들기",
    subject: "데이터 수집부터 시나리오까지",
    footer: "머니게임 · 발표 1  실제 시세를 게임 재료로",
  });
  const k = kit(pres, C);
  const I = {};
  for (const [key, comp] of Object.entries({
    users: fa.FaUsers, play: fa.FaForwardFast, lever: fa.FaArrowTrendUp, rank: fa.FaRankingStar,
    shield: fa.FaShieldHalved, ban: fa.FaBan, memory: fa.FaMemory,
    page: fa.FaArrowRightArrowLeft, split: fa.FaScissors, sort: fa.FaArrowDownWideShort, clock: fa.FaClock,
    key: fa.FaKey, gauge: fa.FaGaugeHigh, globe: fa.FaGlobe,
    filter: fa.FaFilter, tags: fa.FaTags, layers: fa.FaLayerGroup, plug: fa.FaPlug,
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
  pres.addSection({ title: "도입" });
  {
    const s = pres.addSlide({ masterName: "DARK", sectionTitle: "도입" });
    s.addText("머니게임 · 발표 1 / 3", { placeholder: "kicker" });
    s.addText("실제 시세를\n게임 재료로 만들기", { placeholder: "title" });
    s.addText("토스증권 API 실측부터 수집기 · 시세 저장소 · 시나리오까지", { placeholder: "body" });
    // 오른쪽 장식 — 오르는 캔들 몇 개 (상승 빨강 · 하락 파랑)
    const candles = [
      [0, 3.6, 3.95, 3.45, 4.05], [1, 3.9, 3.55, 3.4, 4.0], [2, 3.55, 3.2, 3.1, 3.7], [3, 3.2, 3.35, 3.05, 3.45],
      [4, 3.35, 2.9, 2.8, 3.45], [5, 2.9, 2.55, 2.4, 3.0], [6, 2.55, 2.75, 2.45, 2.85], [7, 2.75, 2.3, 2.2, 2.85],
      [8, 2.3, 1.95, 1.8, 2.4], [9, 1.95, 2.15, 1.85, 2.25], [10, 2.15, 1.6, 1.45, 2.2],
    ]; // [i, 시가 y, 종가 y, 고가 y, 저가 y] — y 가 작을수록 높은 가격
    candles.forEach(([i, o, c, hi, lo]) => {
      const x = 7.0 + i * 0.22;
      const up = c < o;
      const color = up ? C.accent2 : C.accent3;
      k.rect(s, { x: x + 0.065, y: hi, w: 0.02, h: lo - hi, fill: { color } });
      k.rect(s, { x, y: Math.min(o, c), w: 0.15, h: Math.max(Math.abs(o - c), 0.04), fill: { color } });
    });
    s.addNotes(
      "머니게임은 여러 명이 같은 방에서 실제 과거 시세를 압축 재생한 구간을 거래하고, 끝날 때 남은 자산으로 순위를 가리는 웹게임입니다. " +
        "세 번의 발표 중 첫 번째로, 게임의 재료가 되는 시세 데이터를 어떻게 확보하고 다듬었는지 이야기합니다."
    );
  }

  // ───────────────────────── 2. 한 판의 흐름 ─────────────────────────
  {
    const s = content("도입", "한 판은 이렇게 흘러갑니다", "과거 시세를 빠르게 재생하고, 배율을 걸어 사고팔고, 남은 자산으로 순위를 가립니다");
    const steps = [
      [I.users, "방에 모인다", "최대 8명, 혼자면\n존버 봇과 겨룬다"],
      [I.play, "시세 재생", "1초에 캔들 1개,\n종목 이름은 A · B · C"],
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
    const stats = [["240틱", "한 판 = 4분 (1틱 = 1초)"], ["×1 ~ ×3", "일봉 모드 배율"], ["1억 원", "시드머니"]];
    stats.forEach(([v, l], i) => {
      const x = M + i * 3.0;
      k.text(s, v, { x, y: 4.0, w: 2.8, h: 0.6, fontSize: 32, bold: true, color: C.accent1 });
      k.text(s, l, { x, y: 4.62, w: 2.8, h: 0.3, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "한 판의 흐름입니다. 참가자가 방에 모여 준비하면, 실제 과거 시세를 1초에 캔들 하나씩 재생합니다. " +
        "종목 이름과 날짜는 가리고 A, B, C 로만 보여줍니다. 배율을 걸면 손익이 커지지만 증거금만큼 잃으면 강제 청산됩니다. " +
        "끝나면 남은 자산으로 순위를 매기고 실제 종목을 공개합니다. 그래서 이 게임에는 실제 시세 데이터가 반드시 필요합니다."
    );
  }

  // ───────────────────────── 3. 세 번의 발표 ─────────────────────────
  {
    const s = content("도입", "세 번의 발표, 오늘은 데이터", "프로젝트가 진행된 순서대로 나눴습니다");
    const talks = [
      ["발표 1 · 오늘", "실제 시세를\n게임 재료로", "토스 API 실측 → 수집기 → 시세 저장소 → 시나리오"],
      ["발표 2", "정확하고 공정한\n게임 서버", "금액 계산 · 청산 → 방 단위 직렬 처리 → 실시간 통신 · 결과 저장"],
      ["발표 3", "플레이해보고\n고친 것들", "화면 · 봇 · 임시 공개 → 데이터로 내린 결정 → 남은 과제"],
    ];
    const cw = 2.85, gap = 0.225;
    talks.forEach(([tag, t, d], i) => {
      const x = M + i * (cw + gap);
      const today = i === 0;
      k.card(s, { x, y: 1.55, w: cw, h: 2.2, fill: today ? C.accent1 : C.background2, name: `talk-${i + 1}` });
      k.text(s, tag, { x: x + 0.25, y: 1.78, w: cw - 0.5, h: 0.3, fontSize: 12, bold: true, color: today ? C.background1 : C.accent1 });
      k.text(s, t, { x: x + 0.25, y: 2.12, w: cw - 0.5, h: 0.75, fontSize: 17, bold: true, color: today ? C.background1 : C.text1 });
      k.text(s, d, { x: x + 0.25, y: 2.9, w: cw - 0.5, h: 0.75, fontSize: 12, color: today ? C.background2 : C.accent6 });
    });
    const agenda = ["먼저 정한 규칙", "토스 API 실측", "수집기", "시세 저장소", "시나리오"];
    k.text(s, "오늘의 순서", { x: M, y: 4.05, w: 2, h: 0.3, fontSize: 12, bold: true, color: C.accent6 });
    agenda.forEach((a, i) => {
      const x = M + i * 1.82;
      k.num(s, i + 1, { x, y: 4.42, d: 0.38 });
      k.text(s, a, { x: x + 0.48, y: 4.42, w: 1.3, h: 0.38, fontSize: 13, bold: true, valign: "middle" });
    });
    s.addNotes(
      "발표는 세 번에 걸쳐 진행합니다. 오늘은 데이터, 두 번째는 게임 서버, 세 번째는 실제로 플레이해보고 고친 것들입니다. " +
        "오늘은 먼저 정한 규칙, 토스 API 를 직접 측정한 결과, 수집기, 시세 저장소, 시나리오 순서로 이야기합니다."
    );
  }

  // ───────────────────────── 4. 절대 규칙 ─────────────────────────
  pres.addSection({ title: "원칙과 구조" });
  {
    const s = content("원칙과 구조", "코드보다 먼저 정한 세 가지 규칙", "규칙을 깨는 코드는 쓰지 않습니다. 충돌하는 요청은 먼저 지적합니다");
    const rules = [
      [I.shield, "실계좌 보호", "주문 · 계좌 · 잔고 API 를\n호출하는 코드는\n어떤 이유로도 쓰지 않는다", "토큰 하나로 실제 주문이\n나갈 수 있다. 쓰는 API 는\n토큰 · 캔들 · 종목 정보뿐"],
      [I.ban, "게임 중 I/O 금지", "틱 루프가 도는 동안\nDB 쓰기도, 외부 API\n호출도 하지 않는다", "DB 는 판 시작 때 읽기,\n판 종료 때 쓰기 — 두 순간뿐"],
      [I.memory, "상태 테이블 금지", "잔고 · 보유 종목 같은\n「지금 상태」 테이블을\n만들지 않는다", "매 거래 UPDATE 가 생기면\n설계가 무너진다.\n상태는 서버 메모리에만"],
    ];
    const cw = 2.85, gap = 0.225;
    rules.forEach(([ic, t, rule, why], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 1.5, w: cw, h: 3.0, name: `rule-${i + 1}` });
      k.badge(s, ic, { x: x + 0.25, y: 1.72, d: 0.55 });
      k.text(s, t, { x: x + 0.95, y: 1.72, w: cw - 1.1, h: 0.55, fontSize: 17, bold: true, valign: "middle" });
      k.text(s, rule, { x: x + 0.25, y: 2.45, w: cw - 0.5, h: 0.95, fontSize: 14, bold: true });
      k.text(s, why, { x: x + 0.25, y: 3.45, w: cw - 0.5, h: 0.95, fontSize: 12, color: C.accent6 });
    });
    k.text(s, "→ 이 규칙들 때문에 시세 수집기를 게임 서버와 떼어 놓았습니다", { x: M, y: 4.68, w: W - 2 * M, h: 0.35, fontSize: 14, bold: true, color: C.accent1 });
    s.addNotes(
      "코드를 쓰기 전에 깨지 않을 규칙 세 가지를 먼저 정했습니다. 첫째, 토스 API 토큰은 실계좌 주문까지 열리는 자격증명이라 주문이나 계좌 관련 API 는 아예 호출하지 않습니다. " +
        "둘째, 게임이 진행되는 동안에는 DB 나 외부 API 에 접근하지 않습니다. 셋째, 잔고처럼 계속 바뀌는 상태를 테이블에 두지 않고 서버 메모리에만 둡니다. " +
        "이 규칙들이 다음 장의 구조를 결정했습니다."
    );
  }

  // ───────────────────────── 5. 시스템 구성 ─────────────────────────
  {
    const s = content("원칙과 구조", "수집기와 게임 서버를 떼어 놓았다", "게임 서버는 외부 API 를 모릅니다. 시세는 DB 에서만 읽습니다");
    const box = (x, y, w, h, title, sub, fill, color = C.text1, subColor = C.accent6) => {
      k.card(s, { x, y, w, h, fill, name: title });
      k.text(s, title, { x: x + 0.15, y: y + 0.18, w: w - 0.3, h: 0.35, fontSize: 15, bold: true, align: "center", color });
      k.text(s, sub, { x: x + 0.15, y: y + 0.55, w: w - 0.3, h: h - 0.65, fontSize: 11, align: "center", color: subColor });
    };
    const y = 1.6;
    box(M, y, 1.7, 1.15, "토스증권 API", "시세 조회만\n(캔들 · 종목 정보)", C.background2);
    box(2.75, y, 1.9, 1.15, "시세 수집기", "collector 프로파일\n필요할 때 배치 실행", C.background2);
    box(5.0, y, 1.9, 1.15, "MySQL", "시세 · 시나리오 · 결과", C.accent1, C.background1, C.background2);
    box(7.35, y, 2.15, 1.15, "게임 서버", "기본 프로파일, 상주\n판 시작 읽기 · 종료 쓰기", C.background2);
    k.arrow(s, 2.25, y + 0.58, 2.7, y + 0.58);
    k.arrow(s, 4.7, y + 0.58, 4.95, y + 0.58);
    k.arrow(s, 7.3, y + 0.58, 6.95, y + 0.58);
    // 게임 서버 ↔ 브라우저
    k.card(s, { x: 7.35, y: 3.3, w: 2.15, h: 0.75, fill: C.background2, name: "browser" });
    k.text(s, "브라우저 (React)", { x: 7.45, y: 3.3, w: 1.95, h: 0.75, fontSize: 14, bold: true, align: "center", valign: "middle" });
    k.arrow(s, 8.42, y + 1.2, 8.42, 3.25);
    k.arrow(s, 8.58, 3.25, 8.58, y + 1.2);
    k.text(s, "REST · WebSocket", { x: 8.7, y: 2.85, w: 0.9, h: 0.3, fontSize: 9, color: C.accent6 });
    // 핵심 두 줄
    const points = [
      ["하나의 Spring Boot 앱, 두 가지 실행", "엔티티 · 설정을 함께 쓰려고 프로젝트는 하나. 수집은 collector 프로파일로만 실행된다"],
      ["게임 서버는 수집 코드를 타지 않는다", "수집기 빈은 전부 @Profile(\"collector\") — 게임 서버에서는 만들어지지도 않는다"],
    ];
    points.forEach(([t, d], i) => {
      const yy = 3.25 + i * 0.85;
      k.num(s, i + 1, { x: M, y: yy, d: 0.38 });
      k.text(s, t, { x: M + 0.55, y: yy - 0.02, w: 6.2, h: 0.3, fontSize: 14, bold: true });
      k.text(s, d, { x: M + 0.55, y: yy + 0.3, w: 6.2, h: 0.45, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "그래서 구조를 이렇게 나눴습니다. 토스 API 를 부르는 건 시세 수집기뿐이고, 수집기는 필요할 때만 배치로 실행됩니다. " +
        "게임 서버는 외부 API 를 전혀 모르고 DB 에서 시세를 읽기만 합니다. 엔티티와 설정을 같이 쓰려고 프로젝트는 하나로 두되, " +
        "Spring 프로파일로 실행 경로를 나눠 게임 서버에서는 수집 코드가 아예 만들어지지 않게 했습니다."
    );
  }

  // ───────────────────────── 6. 실측 ① 조회 범위 ─────────────────────────
  pres.addSection({ title: "토스 API 실측" });
  {
    const s = content("토스 API 실측", "실측 ① 얼마나 오래된 시세까지 받을 수 있나", "문서에 보존 기간이 없어서, 삼성전자(005930)를 끝까지 받아 봤습니다");
    const stat = (x, value, label, detail) => {
      k.text(s, value, { x, y: 1.5, w: 4.2, h: 0.85, fontSize: 48, bold: true, color: C.accent1 });
      k.text(s, label, { x, y: 2.35, w: 4.2, h: 0.32, fontSize: 16, bold: true });
      k.text(s, detail, { x, y: 2.68, w: 4.2, h: 0.32, fontSize: 12, color: C.accent6 });
    };
    stat(M, "51년 3개월", "일봉 — 1975-06-12부터", "13,735봉 · 마지막 페이지까지 확인 (경계 확정)");
    stat(5.2, "4년 5개월+", "분봉 — 2022-04-19 이전부터", "775,000봉+ · 3,000요청 상한에도 끝 미도달");
    // 시간축 막대 — 1975 ~ 2026
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
      { text: "→ 서둘러 쌓을 필요가 없다. 4년치 = 3,000요청 · 68분이라, 전부 훑지 말고 시나리오에 필요한 구간만 좁혀 받는다", options: { fontSize: 12, color: C.accent6 } },
    ], { x: M + 0.25, y: 4.33, w: W - 2 * M - 0.5, h: 0.62, fontSize: 14 });
    s.addNotes(
      "토스 API 문서에는 데이터를 얼마나 오래 보관하는지 나와 있지 않습니다. 그래서 직접 끝까지 받아 봤습니다. " +
        "일봉은 1975년 6월까지, 51년 넘게 나오고 마지막 페이지 표시로 경계도 확인했습니다. 분봉은 3천 번을 요청해도 끝에 닿지 않아 최소 4년 5개월입니다. " +
        "흔히 1분봉은 최근 몇 주치만 남는다고 하는데 사실이 아니었고, 덕분에 데이터를 서둘러 쌓지 않고 필요한 구간만 받는 쪽으로 설계했습니다."
    );
  }

  // ───────────────────────── 7. 실측 ② 동작 ─────────────────────────
  {
    const s = content("토스 API 실측", "실측 ② 응답은 이렇게 동작한다", "추측으로 코드를 짜지 않고, 각각 실제 요청으로 확인했습니다");
    const facts = [
      [I.page, "중복 없이 이어지는 페이지", "before 는 그 시각을 포함한다. 응답의 nextBefore 를\n그대로 넘기면 겹치지 않는다\n예) 끝 봉 11-20 → nextBefore = 다음 첫 봉 11-19"],
      [I.split, "수정주가는 정확하다", "삼성전자 액면분할(2018-05-04, 50:1) 이전 종가가\nadjusted=false 에서 정확히 50배\n→ 기본값 true 를 그대로 쓴다"],
      [I.sort, "최신순으로 온다", "응답은 시간 내림차순, 게임은 오름차순이 필요하다\n→ 뒤집는 책임은 시세 저장소(M2)에 둔다"],
      [I.clock, "분봉은 하루 720봉", "KRX + NXT 통합이라 08:01 ~ 20:00\n정규장 390분이 아니다. timestamp 는 봉이 「끝난」 시각"],
    ];
    const cw = 4.4, ch = 1.6;
    facts.forEach(([ic, t, d], i) => {
      const x = M + (i % 2) * (cw + 0.2);
      const y = 1.5 + Math.floor(i / 2) * (ch + 0.2);
      k.card(s, { x, y, w: cw, h: ch, name: `fact-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: y + 0.22, d: 0.5 });
      k.text(s, t, { x: x + 0.9, y: y + 0.22, w: cw - 1.1, h: 0.5, fontSize: 16, bold: true, valign: "middle" });
      k.text(s, d, { x: x + 0.22, y: y + 0.82, w: cw - 0.44, h: 0.72, fontSize: 12, color: C.accent6 });
    });
    s.addNotes(
      "응답이 어떻게 동작하는지도 하나씩 확인했습니다. 페이지를 넘길 때 nextBefore 를 그대로 넘기면 겹치는 봉이 없습니다. 같은 명령을 두 번 돌려도 중복 행이 생기지 않는다는 완료 조건이 여기에 달려 있습니다. " +
        "수정주가는 액면분할 전후로 정확히 50배 차이가 나서 기본값을 그대로 씁니다. 응답이 최신순이라 오름차순으로 뒤집는 곳을 정해 두었고, " +
        "1분봉은 대체거래소까지 합쳐 하루 720봉이라 시나리오에서 정규장만 잘라야 한다는 걸 알게 됐습니다."
    );
  }

  // ───────────────────────── 8. 실측 ③ 함정 ─────────────────────────
  {
    const s = content("토스 API 실측", "실측 ③ 수집을 멈춰 세운 함정 세 가지", "모두 문서만 봐서는 알 수 없었고, 실제로 막힌 뒤에 원인을 찾았습니다");
    const traps = [
      [I.gauge, "267", "번 연속이면 429", "속도 제한은 「초당 20회」가\n아니라 토큰 버킷. 평균\n1.35 TPS 로도 버킷이 마른다", "요청 사이 0.3초 → 잔량 19/20\n1,800요청 무사고\n429 → Retry-After · 백오프"],
      [I.key, "608", "번째에 token-revoked", "클라이언트당 유효 토큰은 1개\n다시 발급하면 이전 토큰이\n즉시 죽는다 (동시 실행 탓)", "토큰을 캐시해 재사용하고\n만료 직전에만 재발급\n수집기는 하나만 띄운다"],
      [I.globe, "403", "IP address not allowed", "허용 IP 화이트리스트\nMac 으로 옮기자 토큰 발급부터\n막혔다. 오류 본문은 gzip", "머신 · 네트워크를 옮기면\n개발자 콘솔에 공인 IP 부터"],
    ];
    const cw = 2.85, gap = 0.225;
    traps.forEach(([ic, n, unit, cause, fix], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 1.5, w: cw, h: 3.5, name: `trap-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: 1.7, d: 0.45 });
      k.text(s, n, { x: x + 0.22, y: 2.2, w: cw - 0.44, h: 0.65, fontSize: 40, bold: true, color: C.accent1 });
      k.text(s, unit, { x: x + 0.22, y: 2.85, w: cw - 0.44, h: 0.3, fontSize: 13, bold: true });
      k.text(s, cause, { x: x + 0.22, y: 3.22, w: cw - 0.44, h: 0.85, fontSize: 11, color: C.accent6 });
      k.text(s, fix, { x: x + 0.22, y: 4.1, w: cw - 0.44, h: 0.8, fontSize: 11, bold: true, color: C.text1 });
    });
    s.addNotes(
      "수집을 실제로 멈춰 세운 문제가 세 가지 있었습니다. 첫째, 속도 제한이 초당 몇 회가 아니라 토큰 버킷이라 평균 속도가 낮아도 연속 요청이 쌓이면 막힙니다. 요청 사이에 0.3초를 두고 429 를 받으면 물러섰다 다시 시도합니다. " +
        "둘째, 토큰은 클라이언트당 하나만 살아 있어서 수집기 두 개를 동시에 돌리면 서로의 토큰을 죽입니다. 셋째, 허용 IP 목록이 있어서 노트북을 바꾸자마자 토큰 발급부터 막혔습니다. " +
        "세 가지 모두 수집기의 필수 구현 사항과 운영 수칙으로 문서에 남겼습니다."
    );
  }

  // ───────────────────────── 9. 수집기 ─────────────────────────
  pres.addSection({ title: "데이터 파이프라인" });
  {
    const s = content("데이터 파이프라인", "수집기 — 몇 번을 돌려도 같은 결과", "완료 조건: 같은 명령을 두 번 실행해도 중복 행이 생기지 않는다");
    const steps = [
      ["토큰", "캐시해 재사용,\n만료 직전 재발급"],
      ["200봉씩", "nextBefore 로\n과거 방향으로"],
      ["속도 조절", "0.3초 간격,\n429 면 백오프"],
      ["저장", "배치 upsert,\n겹쳐도 중복 없음"],
      ["수집 이력", "받은 구간을\n기록한다"],
    ];
    const cw = 1.62, gap = 0.215, y = 1.55;
    steps.forEach(([t, d], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y, w: cw, h: 1.35, name: `collect-${i + 1}` });
      k.num(s, i + 1, { x: x + 0.15, y: y + 0.15, d: 0.36 });
      k.text(s, t, { x: x + 0.15, y: y + 0.58, w: cw - 0.3, h: 0.3, fontSize: 14, bold: true });
      k.text(s, d, { x: x + 0.15, y: y + 0.88, w: cw - 0.3, h: 0.42, fontSize: 10.5, color: C.accent6 });
      if (i < steps.length - 1) k.arrow(s, x + cw + 0.02, y + 0.67, x + cw + gap - 0.02, y + 0.67);
    });
    // 결손 구간만 받기
    k.text(s, "결손 구간만 받기 — 받은 구간을 합치고, 빠진 곳만 최신 구간부터 호출한다", { x: M, y: 3.18, w: W - 2 * M, h: 0.32, fontSize: 14, bold: true });
    const bx = M, bw = W - 2 * M, by = 3.65;
    const segs = [[0, 0.38, true], [0.38, 0.47, false], [0.47, 0.86, true], [0.86, 1, false]];
    segs.forEach(([a, b, have], i) => {
      if (have) k.rect(s, { x: bx + a * bw, y: by, w: (b - a) * bw, h: 0.36, fill: { color: C.accent1 }, objectName: `have-${i}` });
      else
        k.rect(s, { x: bx + a * bw, y: by, w: (b - a) * bw, h: 0.36, fill: { color: C.accent4, transparency: 75 }, line: { color: C.accent4, width: 1.25, dashType: "dash" }, objectName: `gap-${i}` });
    });
    k.text(s, "받은 구간", { x: bx + 0.12, y: by, w: 1.5, h: 0.36, fontSize: 11, bold: true, color: C.background1, valign: "middle" });
    k.text(s, "빠진 구간 ②", { x: bx + 0.38 * bw, y: by, w: 0.09 * bw, h: 0.36, fontSize: 10, bold: true, align: "center", valign: "middle" });
    k.text(s, "빠진 구간 ①", { x: bx + 0.86 * bw, y: by, w: 0.14 * bw, h: 0.36, fontSize: 10, bold: true, align: "center", valign: "middle" });
    k.text(s, "과거", { x: bx, y: by + 0.42, w: 1, h: 0.22, fontSize: 10, color: C.accent6 });
    k.text(s, "최근", { x: bx + bw - 1, y: by + 0.42, w: 1, h: 0.22, fontSize: 10, color: C.accent6, align: "right" });
    k.text(s, [
      { text: "다음 실행은 그 뒤의 최근 봉만 받는다. ", options: { bold: true } },
      { text: "봉 간격 이내로 맞닿은 구간은 하나로 합친다. 실제 API 로 다시 확인하는 일은 계정 문제로 대기 중", options: { color: C.accent6 } },
    ], { x: M, y: 4.45, w: W - 2 * M, h: 0.5, fontSize: 12 });
    s.addNotes(
      "수집기의 목표는 몇 번을 돌려도 같은 결과입니다. 토큰을 아껴 쓰고, 200봉씩 과거 방향으로 페이지를 넘기고, 속도를 조절하면서, 배치 upsert 로 저장합니다. " +
        "받은 구간은 수집 이력으로 남겨서, 다음 실행 때는 이미 받은 구간을 건너뛰고 빠진 곳만 최신부터 다시 받습니다. " +
        "이 동작은 가짜 시세 출처로 단위 테스트를 마쳤고, 실제 API 로 한 번 더 확인하는 일만 남았습니다."
    );
  }

  // ───────────────────────── 10. 시세 저장소 ─────────────────────────
  {
    const s = content("데이터 파이프라인", "시세 저장소 — 시세를 읽는 단 하나의 통로", "구현체를 갈아 끼워도 위쪽 코드는 바뀌지 않습니다");
    const cx = M, cw = 5.3;
    k.card(s, { x: cx, y: 1.5, w: cw, h: 0.6, fill: C.background2, name: "callers" });
    k.text(s, "시나리오 · 게임 서버", { x: cx, y: 1.5, w: cw, h: 0.6, fontSize: 14, bold: true, align: "center", valign: "middle" });
    k.arrow(s, cx + cw / 2, 2.13, cx + cw / 2, 2.42);
    k.card(s, { x: cx, y: 2.45, w: cw, h: 0.95, fill: C.accent1, name: "interface" });
    k.badge(s, I.plug, { x: cx + 0.2, y: 2.62, d: 0.6, fill: C.text2 });
    k.text(s, [
      { text: "PriceDataProvider", options: { bold: true, fontSize: 16, breakLine: true } },
      { text: "getCandles(종목, 단위, from, to) — 고정된 인터페이스", options: { fontSize: 12 } },
    ], { x: cx + 0.95, y: 2.55, w: cw - 1.1, h: 0.75, color: C.background1, valign: "middle" });
    k.arrow(s, cx + 1.3, 3.43, cx + 1.3, 3.72);
    k.arrow(s, cx + cw - 1.3, 3.43, cx + cw - 1.3, 3.72);
    const impl = [["CSV 구현체", "초기 개발용 · 지금 사용"], ["DB 구현체", "운영용 · MySQL"]];
    impl.forEach(([t, d], i) => {
      const x = cx + i * (cw / 2 + 0.1);
      k.card(s, { x, y: 3.75, w: cw / 2 - 0.1, h: 0.85, fill: C.background2, name: `impl-${i + 1}` });
      k.text(s, t, { x, y: 3.85, w: cw / 2 - 0.1, h: 0.3, fontSize: 14, bold: true, align: "center" });
      k.text(s, d, { x, y: 4.17, w: cw / 2 - 0.1, h: 0.3, fontSize: 11, align: "center", color: C.accent6 });
    });
    k.text(s, "설정 한 줄로 교체 — marketdata.provider = csv | db", { x: cx, y: 4.68, w: cw, h: 0.3, fontSize: 11, color: C.accent6, align: "center" });
    // 규약
    const rx = 6.1, rw = W - M - rx;
    k.text(s, "약속한 규칙", { x: rx, y: 1.5, w: rw, h: 0.32, fontSize: 15, bold: true });
    const rules = [
      ["from · to 는 KST, 양끝 포함", "DB 시각 규약과 같다"],
      ["항상 시간 오름차순", "API 의 최신순을 여기서 뒤집는다"],
      ["빈 구간은 빈 목록", "종목 자체가 없으면 예외"],
      ["정규장 자르기는 하지 않는다", "그건 시나리오의 일"],
    ];
    rules.forEach(([t, d], i) => {
      const y = 1.95 + i * 0.66;
      k.rect(s, { x: rx, y: y + 0.08, w: 0.1, h: 0.1, fill: { color: C.accent1 } });
      k.text(s, t, { x: rx + 0.22, y, w: rw - 0.22, h: 0.28, fontSize: 13, bold: true });
      k.text(s, d, { x: rx + 0.22, y: y + 0.28, w: rw - 0.22, h: 0.28, fontSize: 11, color: C.accent6 });
    });
    k.card(s, { x: rx, y: 4.62, w: rw, h: 0.42, fill: C.background2, line: C.accent4, name: "status" });
    k.text(s, "DB 구현체는 임시 데이터로만 검증 — 실데이터 확인은 API 재개 후", { x: rx + 0.12, y: 4.62, w: rw - 0.24, h: 0.42, fontSize: 10, valign: "middle" });
    s.addNotes(
      "게임 서버가 시세를 읽는 길은 이 인터페이스 하나뿐입니다. 구현체는 CSV 와 DB 두 개이고 설정 한 줄로 바꿉니다. " +
        "규칙도 정해 두었습니다. 시각은 한국 시간에 양끝 포함, 결과는 항상 오름차순, 그리고 정규장 자르기 같은 게임 규칙은 여기서 하지 않습니다. " +
        "지금은 API 계정 사정으로 저장해 둔 CSV 로 진행하고 있고, DB 구현체는 임시 데이터로만 검증한 상태라는 점도 솔직히 말씀드립니다."
    );
  }

  // ───────────────────────── 11. 시나리오 구조 ─────────────────────────
  {
    const s = content("데이터 파이프라인", "시나리오 — 한 판에 쓰는 시세 묶음", "종목 1~4개 + 같은 시각의 241봉. 운영자가 미리 골라 둡니다");
    const labels = ["A", "B", "C"];
    labels.forEach((l, i) => {
      const y = 1.6 + i * 0.72;
      k.num(s, l, { x: M, y, d: 0.5, fill: C.text1 });
      k.rect(s, { x: M + 0.7, y: y + 0.08, w: 0.45, h: 0.34, fill: { color: C.accent4 } });
      k.rect(s, { x: M + 1.2, y: y + 0.08, w: 4.0, h: 0.34, fill: { color: C.accent1 } });
      if (i === 0) {
        k.text(s, "시작가", { x: M + 0.7, y: y + 0.08, w: 0.45, h: 0.34, fontSize: 9, bold: true, align: "center", valign: "middle" });
        k.text(s, "240틱 — 1초에 1봉", { x: M + 1.2, y: y + 0.08, w: 4.0, h: 0.34, fontSize: 11, bold: true, color: C.background1, align: "center", valign: "middle" });
      }
    });
    k.text(s, "1봉 + 240봉 = 241봉. 모든 종목의 i 번째 봉은 같은 시각", { x: M, y: 3.78, w: 5.2, h: 0.3, fontSize: 12, color: C.accent6 });
    const rules = [
      [I.tags, "라벨로 종목을 가린다", "등록할 때 A~D 를 무작위로 붙인다. 매번 A 가 같은 종목이면 이름을 가리는 의미가 없다"],
      [I.layers, "시각이 어긋나면 시작하지 않는다", "한 종목만 거래정지로 봉이 빠지면 틱마다 종목별 날짜가 달라진다"],
      [I.filter, "등록은 사람이 고른다", "관리 커맨드는 적격 후보를 이동폭 순으로 보여줄 뿐, 자동 등록하지 않는다"],
    ];
    rules.forEach(([ic, t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.badge(s, ic, { x: 5.95, y, d: 0.48 });
      k.text(s, t, { x: 6.58, y: y - 0.02, w: W - M - 6.58, h: 0.32, fontSize: 14, bold: true });
      k.text(s, d, { x: 6.58, y: y + 0.3, w: W - M - 6.58, h: 0.75, fontSize: 11, color: C.accent6 });
    });
    k.card(s, { x: M, y: 4.25, w: 5.2, h: 0.75, fill: C.background2, name: "command" });
    k.text(s, [
      { text: "--action=candidates  → 후보 보기", options: { breakLine: true } },
      { text: "--action=register --start=2024-05-09  → 등록", options: {} },
    ], { x: M + 0.2, y: 4.3, w: 4.9, h: 0.65, fontSize: 11, fontFace: "Courier New", valign: "middle" });
    s.addNotes(
      "시나리오는 한 판에 쓰는 시세 묶음입니다. 종목 1개에서 4개까지, 각각 시작가 1봉과 240틱을 합친 241봉을 씁니다. " +
        "종목 이름을 가리려고 라벨을 붙이는데, 등록할 때 무작위로 붙입니다. 모든 종목의 같은 순번 봉은 같은 시각이어야 하고, 하나라도 어긋나면 판을 시작하지 않습니다. " +
        "후보는 관리 커맨드가 보여주지만, 등록은 사람이 고릅니다."
    );
  }

  // ───────────────────────── 12. 분봉 09:01 ~ 15:20 ─────────────────────────
  pres.addSection({ title: "시나리오 품질" });
  {
    const s = content("시나리오 품질", "분봉은 09:01 ~ 15:20 만 쓴다", "시간외와 경계 봉에는 「거래량 0」 봉이 몰려 있습니다 — 화면이 멈춘 것처럼 보입니다");
    // 하루 타임라인 08:00 ~ 20:00
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
      ["09:00 봉 = [08:59, 09:00) 개장 전", "14일 모두 거래량 0", C.accent2],
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
    // 오른쪽 숫자
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
      "분봉은 대체거래소까지 합쳐 하루 720봉이지만 전부 쓰면 안 됩니다. 시간대별로 재보니 프리마켓에는 거래량 0 봉이 15% 섞여 있고, 애프터는 거래가 정규장의 8분의 1 수준입니다. " +
        "특히 9시 봉과 15시 21분부터 30분까지는 14일 내내 거래량이 0 이었습니다. timestamp 가 봉이 끝난 시각이라 9시 봉은 사실 개장 전 1분이고, 15시 21분부터는 종가 단일가 구간이기 때문입니다. " +
        "이 두 경계를 빼면 거래량 0 봉이 2.8% 에서 0.3% 로, 최대 연속 정지가 11봉에서 1봉으로 줄어듭니다. 1틱이 1초라서 11봉 정지는 화면이 11초 멈추는 것과 같습니다."
    );
  }

  // ───────────────────────── 13. 적격성 검사 ─────────────────────────
  {
    const s = content("시나리오 품질", "적격성 검사 — 멈추거나 밋밋한 구간은 버린다", "하나라도 넘으면 시나리오로 쓰지 않습니다. 값은 상수로 빼 두고 플레이하며 조정합니다");
    const checks = [
      ["거래량 0 봉 비율", "≤ 5%", "정상 구간 실측은 0.3%. 여유 있게 통과한다"],
      ["연속 정지", "≤ 5봉", "시간외가 섞인 구간은 비율 4.6% 로 통과하지만 연속 11봉에서 걸린다. 비율만으론 못 거른다"],
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
      "그래서 시나리오로 쓰기 전에 적격성 검사를 합니다. 거래량 0 봉 비율은 5% 이하, 연속으로 멈추는 건 5봉 이하여야 합니다. 비율만 보면 흩어진 12봉과 12초 연속 정지를 구분할 수 없어서 연속 검사를 따로 둡니다. " +
        "변동성 하한은 분봉과 일봉을 따로 둡니다. 오른쪽 표처럼 중앙값이 분봉 2%, 일봉 46% 로 스케일이 완전히 다르기 때문입니다. 값은 확정이 아니라 상수로 빼 두고 플레이하며 조정하고 있습니다."
    );
  }

  // ───────────────────────── 14. 배율 vs 시나리오 ─────────────────────────
  {
    const s = content("시나리오 품질", "실측이 바꾼 결정 — 밸런스는 시나리오로 맞춘다", "240봉 구간을 한 봉씩 밀어 가며 전수 계산 — 청산이 한 번이라도 나는 구간의 비율");
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
      ["분봉은 ×20 까지 청산 0%", "하루 시가 → 저가 −10% 이하는 169일에 한 번. 배율을 올려도 「망할 위험」이 생기지 않는다"],
      ["종목 차이가 배율 차이보다 크다", "−5% 기준 에코프로비엠 11.89% vs 삼성전자 1.60% — 7배"],
      ["그래서 시나리오를 고른다", "평범한 날 대신 변동이 큰 구간을 고른다 → 적격성 검사의 이동폭 하한"],
    ];
    points.forEach(([t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.num(s, i + 1, { x: tx, y, d: 0.36 });
      k.text(s, t, { x: tx + 0.5, y: y - 0.01, w: tw - 0.5, h: 0.32, fontSize: 14, bold: true });
      k.text(s, d, { x: tx + 0.5, y: y + 0.33, w: tw - 0.5, h: 0.75, fontSize: 11, color: C.accent6 });
    });
    k.text(s, "발표 3 예고 — 분봉 모드는 결국 동결됩니다. 이 그래프가 이유입니다", { x: tx, y: 4.72, w: tw, h: 0.3, fontSize: 10, italic: true, color: C.accent1 });
    s.addNotes(
      "배율 상한을 어떻게 정할지 고민하다가 직접 계산해 봤습니다. 240봉 구간을 한 봉씩 밀어 가며, 배율별로 청산이 한 번이라도 나는 구간이 얼마나 되는지 셌습니다. " +
        "일봉은 배율 3 에서도 13% 구간에서 청산이 나지만, 분봉은 배율 20 까지도 0% 였습니다. 하루 안에서 10% 넘게 빠지는 날 자체가 169일에 한 번뿐이기 때문입니다. " +
        "또 같은 조건에서 종목 간 차이가 7배나 났습니다. 그래서 밸런스는 배율이 아니라 변동이 큰 구간을 골라 시나리오로 맞추기로 했고, 이 그래프는 세 번째 발표에서 분봉 모드를 동결한 이유로 다시 나옵니다."
    );
  }

  // ───────────────────────── 15. 정리 ─────────────────────────
  pres.addSection({ title: "정리" });
  {
    const s = pres.addSlide({ masterName: "DARK_TOP", sectionTitle: "정리" });
    s.addText("정리", { placeholder: "kicker" });
    s.addText("데이터가 게임이 되기까지", { placeholder: "title" });
    const items = [
      ["규칙을 먼저 정했다", "실계좌 보호 · 게임 중 I/O 금지 · 상태는 메모리에"],
      ["문서 대신 실측으로 정했다", "조회 범위, 페이지, 속도 제한, 거래량 0 봉, 배율별 청산"],
      ["데이터 → 시나리오 길을 만들었다", "수집기 · 시세 저장소 · 적격성 검사 · 후보 추출"],
    ];
    items.forEach(([t, d], i) => {
      const y = 1.95 + i * 0.95;
      k.num(s, i + 1, { x: M, y, d: 0.45 });
      k.text(s, t, { x: M + 0.65, y: y - 0.04, w: 5.3, h: 0.34, fontSize: 18, bold: true, color: C.background1 });
      k.text(s, d, { x: M + 0.65, y: y + 0.33, w: 5.3, h: 0.3, fontSize: 12, color: C.background2 });
    });
    const nx = 6.6, nw = W - M - nx;
    k.card(s, { x: nx, y: 1.85, w: nw, h: 1.55, fill: "1B2233", name: "remaining" });
    k.text(s, [
      { text: "남은 과제", options: { bold: true, fontSize: 13, color: HEX.accent4, breakLine: true } },
      { text: "실데이터 검증과 데이터 확장\n지금 CSV 는 2년 8개월치라\n겹치지 않는 일봉 판이 2개뿐\nAPI 재개 후 넓힌다", options: { fontSize: 11, color: HEX.lt2 } },
    ], { x: nx + 0.2, y: 2.0, w: nw - 0.4, h: 1.3 });
    k.card(s, { x: nx, y: 3.6, w: nw, h: 1.3, fill: C.accent1, name: "next" });
    k.text(s, [
      { text: "다음 발표", options: { bold: true, fontSize: 12, breakLine: true } },
      { text: "정확하고 공정하게 돌아가는 게임 서버", options: { bold: true, fontSize: 15 } },
    ], { x: nx + 0.2, y: 3.75, w: nw - 0.4, h: 1.0, color: C.background1 });
    s.addNotes(
      "정리하겠습니다. 첫째, 코드를 쓰기 전에 깨지 않을 규칙을 먼저 정했습니다. 둘째, 문서에 없거나 애매한 것은 직접 측정해서 결정했습니다. 셋째, 그 결과로 시세를 받아 저장하고, 검사해서 시나리오로 만드는 길이 완성됐습니다. " +
        "남은 과제는 데이터를 더 넓히는 것입니다. 다음 발표에서는 이 시나리오를 받아 실제로 게임을 돌리는 서버를 이야기하겠습니다."
    );
  }

  await finish(pres, OUT);
  console.log(`만들었습니다: ${OUT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
