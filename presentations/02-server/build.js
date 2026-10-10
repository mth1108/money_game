// 발표 — 정확하고 공정한 게임 서버 만들기 (중간 점검, 15분 안팎, 14장)
// 범위: 게임 규칙(주문 · 청산 · 반올림 · 수수료 · 잔고 · 순위), 동시 진행, 실시간 전달, 검증.
// 구현 · 코드 이야기는 넣지 않는다 (클래스 · 어노테이션 · 명령어 이름 금지). 봇과 실제 게임 화면은 범위 밖.
// 말투 (2026-10-10 사용자): 슬라이드는 전부 개조식. 발표자 노트는 읽는 대본이라 합니다체.
//   긴 줄표, 꺾쇠괄호, 화살표 잇기는 쓰지 않는다. 영어 뒤 조사는 붙여 쓴다.
// 근거는 CLAUDE.md §1.2 · §1.5 · §1.6 · §3 M4~M8 · §8 시드머니, 엔진 테스트(GameSessionFixtureTest)다.
//   NODE_PATH=<도구 폴더>/node_modules node presentations/02-server/build.js
const path = require("path");
const fa = require("react-icons/fa6");
const { HEX, W, M, newDeck, icon, kit, finish } = require("../common/deck-kit");

const OUT = path.join(__dirname, "moneygame-server.pptx");

async function main() {
  const { pres, C } = newDeck({
    title: "머니게임: 정확하고 공정한 게임 서버 만들기",
    subject: "배율과 청산 규칙 설계, 실시간 대전 서버",
    footer: "머니게임 | 정확하고 공정한 게임 서버",
  });
  const k = kit(pres, C);
  const I = {};
  for (const [key, comp] of Object.entries({
    calc: fa.FaCalculator, fair: fa.FaScaleBalanced, think: fa.FaHourglassHalf,
    users: fa.FaUsers, play: fa.FaForwardFast, lever: fa.FaArrowTrendUp, rank: fa.FaRankingStar,
    bolt: fa.FaBolt, receipt: fa.FaReceipt, wallet: fa.FaWallet,
    list: fa.FaListOl, tower: fa.FaTowerBroadcast, flask: fa.FaFlask, db: fa.FaDatabase,
  })) {
    I[key] = await icon(comp);
  }

  const content = (section, title, lead) => {
    const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: section });
    s.addText(title, { placeholder: "title" });
    s.addText(lead, { placeholder: "lead" });
    return s;
  };
  const S = pres.shapes;

  // ───────────────────────── 1. 표지 ─────────────────────────
  pres.addSection({ title: "소개" });
  {
    const s = pres.addSlide({ masterName: "DARK", sectionTitle: "소개" });
    s.addText("머니게임 | 중간 점검", { placeholder: "kicker" });
    s.addText("정확하고 공정한\n게임 서버 만들기", { placeholder: "title" });
    s.addText("배율과 청산 규칙 설계\n여러 명이 함께하는 실시간 진행", { placeholder: "body" });
    s.addText("", { placeholder: "presenter" });
    // 오른쪽 장식 — 내려가다 청산선에 닿는 캔들 (상승 빨강 · 하락 파랑) + 청산선
    const candles = [
      [0, 1.6, 1.85, 1.5, 1.95], [1, 1.85, 1.7, 1.6, 1.95], [2, 1.7, 2.05, 1.65, 2.15], [3, 2.05, 2.3, 1.95, 2.4],
      [4, 2.3, 2.15, 2.05, 2.4], [5, 2.15, 2.55, 2.1, 2.65], [6, 2.55, 2.8, 2.45, 2.9], [7, 2.8, 2.65, 2.55, 2.9],
      [8, 2.65, 3.05, 2.6, 3.15], [9, 3.05, 3.35, 2.95, 3.5], [10, 3.35, 3.6, 3.25, 3.75],
    ]; // [i, 시가 y, 종가 y, 고가 y, 저가 y] — y 가 클수록 낮은 가격
    candles.forEach(([i, o, c, hi, lo]) => {
      const x = 7.0 + i * 0.22;
      const color = c < o ? C.accent2 : C.accent3;
      k.rect(s, { x: x + 0.065, y: hi, w: 0.02, h: lo - hi, fill: { color } });
      k.rect(s, { x, y: Math.min(o, c), w: 0.15, h: Math.max(Math.abs(o - c), 0.04), fill: { color } });
    });
    k.line(s, { x: 6.85, y: 3.55, w: 2.65, h: 0, line: { color: C.accent4, width: 1.5, dashType: "dash" } });
    k.text(s, "청산선", { x: 8.75, y: 3.6, w: 0.75, h: 0.25, fontSize: 10, bold: true, color: C.accent4, align: "right" });
    s.addNotes(
      "머니게임은 여러 명이 같은 방에서 실제 과거 시세를 빠르게 재생하며 거래하고, 끝날 때 남은 자산으로 순위를 가리는 웹게임입니다. " +
        "오늘은 배율과 청산을 포함한 게임 규칙을 어떻게 설계했는지, 그리고 여러 명이 동시에 플레이해도 결과가 정확하고 공정하도록 서버를 어떻게 구성하고 검증했는지 말씀드리겠습니다."
    );
  }

  // ───────────────────────── 2. 개요와 설계 목표 ─────────────────────────
  {
    const s = content("소개", "개요와 설계 목표 3가지", "과거 시세 압축 재생, 배율 거래, 남은 자산으로 순위 결정. 실제 시세 데이터와 게임 구간 선정은 완료");
    const steps = [[I.users, "방 입장"], [I.play, "시세 재생"], [I.lever, "배율 거래"], [I.rank, "자산 순위"]];
    const sw = 2.05, sg = 0.267;
    steps.forEach(([ic, t], i) => {
      const x = M + i * (sw + sg);
      k.card(s, { x, y: 1.5, w: sw, h: 0.75, name: `flow-${i + 1}` });
      k.badge(s, ic, { x: x + 0.15, y: 1.62, d: 0.5 });
      k.text(s, `${i + 1}. ${t}`, { x: x + 0.75, y: 1.5, w: sw - 0.85, h: 0.75, fontSize: 13, bold: true, valign: "middle" });
      if (i < steps.length - 1) k.arrow(s, x + sw + 0.03, 1.875, x + sw + sg - 0.03, 1.875);
    });
    const goals = [
      [I.calc, "정확", "금액 오차 0원", "소수 오차 없는 십진 계산\n반올림 방향을 대상별로 명시\n경계값을 테스트로 고정"],
      [I.fair, "공정", "같은 시세, 같은 순서", "방마다 한 줄 처리로 순서 보장\n게임 중 외부 호출 없음\n모두에게 같은 틱을 동시에 전달"],
      [I.think, "고민 유지", "고민이 남는 규칙", "수수료 면제 구간 없음\n청산돼도 남은 현금으로 재기\n남의 포지션과 배율 공개"],
    ];
    const cw = 2.85, gap = 0.225;
    goals.forEach(([ic, t, sub, d], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 2.5, w: cw, h: 2.5, fill: i === 0 ? C.background2 : C.background2, name: `goal-${i + 1}` });
      k.badge(s, ic, { x: x + 0.22, y: 2.7, d: 0.55 });
      k.text(s, t, { x: x + 0.9, y: 2.7, w: cw - 1.1, h: 0.3, fontSize: 18, bold: true, color: C.accent1 });
      k.text(s, sub, { x: x + 0.9, y: 3.0, w: cw - 1.1, h: 0.28, fontSize: 12, bold: true });
      k.text(s, d, { x: x + 0.22, y: 3.5, w: cw - 0.44, h: 1.35, fontSize: 12, color: C.accent6, paraSpaceAfter: 4 });
    });
    s.addNotes(
      "머니게임은 실제 과거 시세를 빠르게 재생하고, 배율을 걸어 거래하고, 끝날 때 남은 자산으로 순위를 가리는 게임입니다. 시세 데이터와 게임에 쓸 구간은 이미 준비되어 있습니다. " +
        "서버를 만들면서 세 가지를 목표로 삼았습니다. 첫째, 금액 계산에 오차가 없을 것. 둘째, 모든 참가자가 같은 시세를 같은 순서로 받을 것. 셋째, 팔지 말지 고민하는 게임의 핵심이 규칙 때문에 사라지지 않을 것입니다. " +
        "이후 슬라이드는 이 세 가지를 어떻게 지켰는지에 대한 내용입니다."
    );
  }

  // ───────────────────────── 3. 주문 계산 ─────────────────────────
  pres.addSection({ title: "게임 규칙" });
  {
    const s = content("게임 규칙", "주문 계산: 정수 수량과 증거금 역산", "입력한 증거금은 상한, 실제 차감액은 체결 수량에서 역산");
    k.card(s, { x: M, y: 1.5, w: 5.3, h: 0.62, fill: C.background2, name: "input" });
    k.text(s, [
      { text: "입력  ", options: { bold: true, color: C.accent1 } },
      { text: "증거금 상한 1,000만 원, 배율 ×3, 진입가 79,700원, 시드 1억 원", options: {} },
    ], { x: M + 0.2, y: 1.5, w: 5.0, h: 0.62, fontSize: 12, valign: "middle" });
    const rows = [
      ["수량", "내림(1,000만 × 3 ÷ 79,700)", "376주"],
      ["실제 증거금", "376 × 79,700 ÷ 3", "9,989,066.67원"],
      ["수수료", "376 × 79,700 × 0.15%", "44,950.80원"],
      ["남은 현금", "1억 − 실제 증거금 − 수수료", "89,965,982.53원"],
    ];
    rows.forEach(([t, f, v], i) => {
      const y = 2.3 + i * 0.68;
      k.num(s, i + 1, { x: M, y: y + 0.08, d: 0.4 });
      k.text(s, t, { x: M + 0.55, y, w: 1.3, h: 0.56, fontSize: 13, bold: true, valign: "middle" });
      k.text(s, f, { x: M + 1.85, y, w: 2.1, h: 0.56, fontSize: 11, color: C.accent6, valign: "middle" });
      k.text(s, v, { x: M + 3.85, y, w: 1.45, h: 0.56, fontSize: 13, bold: true, color: C.accent1, align: "right", valign: "middle" });
      if (i < rows.length - 1) k.line(s, { x: M + 0.55, y: y + 0.62, w: 4.75, h: 0, line: { color: C.background2, width: 0.75 } });
    });
    const rx = 6.1, rw = W - M - rx;
    k.text(s, "역산하는 이유", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 14, bold: true });
    const why = [
      ["증거금 × 배율 = 포지션 금액", "두 값이 정확히 일치, 청산가 계산 오차 없음"],
      ["잔돈 반환 방식은 기각", "증거금과 포지션 크기가 어긋난 채 남음\n청산 계산이 틀어짐"],
      ["수량이 0이면 주문 거부", "증거금이 1주 값에 못 미침, 사유는 증거금 부족"],
    ];
    why.forEach(([t, d], i) => {
      const y = 1.95 + i * 1.0;
      k.card(s, { x: rx, y, w: rw, h: 0.88, name: `why-${i + 1}` });
      k.text(s, t, { x: rx + 0.2, y: y + 0.1, w: rw - 0.4, h: 0.3, fontSize: 13, bold: true });
      k.text(s, d, { x: rx + 0.2, y: y + 0.42, w: rw - 0.4, h: 0.42, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "주문 하나가 어떻게 계산되는지 예시로 보여드리겠습니다. 증거금을 1,000만 원까지 쓰겠다고 하고 배율 3으로 79,700원짜리 종목을 사면, 수량은 소수점을 버려 376주가 됩니다. " +
        "실제로 빠지는 증거금은 입력한 1,000만 원이 아니라 376주를 기준으로 거꾸로 계산한 998만 9천 원 정도입니다. " +
        "이렇게 하면 증거금에 배율을 곱한 값이 포지션 금액과 정확히 같아져서 청산가 계산에 오차가 생기지 않습니다. 남는 돈을 돌려주는 방식은 이 등식이 깨져서 쓰지 않았습니다."
    );
  }

  // ───────────────────────── 4. 청산 판정 ─────────────────────────
  {
    const s = content("게임 규칙", "청산 판정: 등호 포함, 배율 3으로 경계값 검증", "청산가 = 진입가 × (1 − 1 ÷ 배율), 현재가가 청산가 이하이면 강제 청산");
    k.text(s, "배율별 청산선 (진입가 대비)", { x: M, y: 1.5, w: 4.4, h: 0.3, fontSize: 13, bold: true });
    const hdr = (t) => ({ text: t, options: { bold: true, color: HEX.lt1, fill: { color: HEX.accent1 }, align: "center" } });
    const cell = (t, o = {}) => ({ text: t, options: { align: "center", ...o } });
    s.addTable(
      [
        [hdr("배율"), hdr("청산선"), hdr("허용 모드")],
        [cell("×1"), cell("없음"), cell("일봉, 분봉")],
        [cell("×2"), cell("−50%"), cell("일봉")],
        [cell("×3"), cell("−33.3%", { bold: true }), cell("일봉, 분봉")],
        [cell("×5"), cell("−20%"), cell("분봉")],
        [cell("×10"), cell("−10%"), cell("분봉")],
      ],
      { x: M, y: 1.9, w: 4.4, colW: [1.1, 1.6, 1.7], rowH: 0.34, fontSize: 12, color: HEX.dk1, border: { type: "solid", pt: 0.75, color: "D9DCE3" }, valign: "middle" }
    );
    k.text(s, "예: 진입가 79,700원, 배율 ×3이면 청산가 약 53,133원\n청산 시 그 포지션의 증거금만 소멸, 나머지 현금은 유지", { x: M, y: 4.1, w: 4.4, h: 0.6, fontSize: 11, color: C.accent6 });
    const rx = 5.3, rw = W - M - rx;
    k.text(s, "경계값 테스트 (배율 3)", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 13, bold: true });
    const cases = [
      ["청산가 + 0.0001", "생존", C.accent5],
      ["청산가와 같음", "청산", C.accent2],
      ["청산가 − 0.0001", "청산", C.accent2],
    ];
    cases.forEach(([t, v, color], i) => {
      const y = 1.9 + i * 0.62;
      k.card(s, { x: rx, y, w: rw, h: 0.52, name: `edge-${i + 1}` });
      k.text(s, t, { x: rx + 0.2, y, w: 2.4, h: 0.52, fontSize: 13, bold: true, valign: "middle" });
      k.text(s, v, { x: rx + rw - 1.2, y, w: 1.0, h: 0.52, fontSize: 14, bold: true, color, align: "right", valign: "middle" });
    });
    k.card(s, { x: rx, y: 3.85, w: rw, h: 1.15, fill: C.background2, name: "why3" });
    k.text(s, [
      { text: "배율 3으로 검증하는 이유", options: { bold: true, breakLine: true } },
      { text: "1 ÷ 3은 무한소수라 반올림 오차가 생김\n배율 2(1 ÷ 2 = 0.5)로만 테스트하면 이 문제를 못 잡음", options: { fontSize: 11, color: C.accent6 } },
    ], { x: rx + 0.2, y: 3.95, w: rw - 0.4, h: 0.95, fontSize: 13 });
    s.addNotes(
      "청산가는 진입가에 1 빼기 1/배율을 곱한 값입니다. 배율 3이면 진입가에서 33% 떨어진 지점이고, 현재가가 청산가와 같거나 낮으면 강제로 청산됩니다. 청산되면 그 포지션에 건 증거금만 사라지고 나머지 현금은 그대로 남습니다. " +
        "경계값은 배율 3으로 테스트합니다. 3분의 1은 무한소수라 반올림 오차가 생기는데, 배율 2로만 테스트하면 이런 문제를 발견할 수 없기 때문입니다. 청산가보다 0.0001 높으면 생존, 같거나 낮으면 청산되는 것을 확인했습니다."
    );
  }

  // ───────────────────────── 5. 반올림 방향 ─────────────────────────
  {
    const s = content("게임 규칙", "반올림 방향: 계산 오차만 플레이어 유리", "모든 금액은 소수 오차 없는 십진 계산, 반올림은 마지막 단계에서 한 번만");
    const hdr = (t) => ({ text: t, options: { bold: true, color: HEX.lt1, fill: { color: HEX.accent1 }, align: "center" } });
    const cell = (t, o = {}) => ({ text: t, options: { align: "center", ...o } });
    s.addTable(
      [
        [hdr("대상"), hdr("자릿수"), hdr("방향"), hdr("이유")],
        [cell("중간 계산"), cell("소수 8자리"), cell("반올림"), cell("나눗셈마다 같은 규칙", { align: "left" })],
        [cell("청산가", { bold: true }), cell("소수 4자리"), cell("내림", { bold: true, color: HEX.accent1 }), cell("늦게 청산, 플레이어에게 유리", { align: "left" })],
        [cell("수수료", { bold: true }), cell("소수 4자리"), cell("올림", { bold: true, color: HEX.accent1 }), cell("잔고가 음수로 새는 것 방지", { align: "left" })],
      ],
      { x: M, y: 1.55, w: W - 2 * M, colW: [1.8, 1.6, 1.4, 4.2], rowH: 0.42, fontSize: 13, color: HEX.dk1, border: { type: "solid", pt: 0.75, color: "D9DCE3" }, valign: "middle" }
    );
    const cards = [
      ["원칙", "애매하면 플레이어에게 유리하게\n단 잔고는 보수적으로", C.background2, C.text1],
      ["적용 범위", "반올림처럼 통제할 수 없는\n계산 오차에만 적용\n게임 규칙에는 미적용", C.background2, C.text1],
      ["규칙에 적용하면", "특정 플레이 방식에 보너스\n의사결정 자체가 사라짐", C.background2, C.text1],
    ];
    const cw = 2.85, gap = 0.225;
    cards.forEach(([t, d], i) => {
      const x = M + i * (cw + gap);
      k.card(s, { x, y: 3.5, w: cw, h: 1.5, name: `round-${i + 1}` });
      k.text(s, t, { x: x + 0.22, y: 3.65, w: cw - 0.44, h: 0.3, fontSize: 14, bold: true, color: C.accent1 });
      k.text(s, d, { x: x + 0.22, y: 4.0, w: cw - 0.44, h: 0.9, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "금액은 모두 소수 오차가 없는 십진 계산으로 처리하고, 반올림은 마지막 단계에서 한 번만 합니다. 대상마다 방향을 정해 두었습니다. " +
        "청산가는 내림해서 청산이 조금이라도 늦게 일어나게 했고, 수수료는 올림해서 잔고가 음수로 새지 않게 했습니다. " +
        "원칙은 애매하면 플레이어에게 유리하게, 단 잔고는 보수적으로입니다. 다만 이 원칙은 반올림처럼 플레이어가 통제할 수 없는 오차에만 적용합니다. 게임 규칙에 적용하면 특정 플레이 방식에 보너스를 주게 되기 때문입니다."
    );
  }

  // ───────────────────────── 6. 수수료 ─────────────────────────
  {
    const s = content("게임 규칙", "수수료: 진입 1회, 종료 1회, 면제 없음", "수수료율 0.15%, 종료 사유와 관계없이 같은 규칙");
    const rows = [
      ["진입 (매수)", "진입가"],
      ["정상 매도", "매도 시점 현재가"],
      ["강제 청산", "청산된 틱의 현재가"],
      ["판 종료 시 정리", "마지막 틱의 현재가"],
    ];
    k.text(s, "부과 시점과 기준가", { x: M, y: 1.5, w: 4.4, h: 0.3, fontSize: 13, bold: true });
    rows.forEach(([t, base], i) => {
      const y = 1.9 + i * 0.75;
      k.card(s, { x: M, y, w: 4.4, h: 0.62, name: `fee-${i + 1}` });
      k.text(s, t, { x: M + 0.2, y, w: 1.9, h: 0.62, fontSize: 13, bold: true, valign: "middle" });
      k.text(s, base, { x: M + 2.0, y, w: 1.55, h: 0.62, fontSize: 11, color: C.accent6, valign: "middle" });
      k.text(s, "부과", { x: M + 3.6, y, w: 0.6, h: 0.62, fontSize: 12, bold: true, color: C.accent1, align: "right", valign: "middle" });
    });
    const rx = 5.3, rw = W - M - rx;
    k.text(s, "종료 정리만 면제하면", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 13, bold: true });
    const chain = [
      "마지막 직전에 파는 쪽이 수수료를 더 냄",
      "합리적인 플레이어는 손절하지 않게 됨",
      "팔지 말지 고민하게 만든다는 목적이 사라짐",
      "끝까지 들고 가는 전략만 구조적으로 유리",
    ];
    chain.forEach((t, i) => {
      const y = 1.9 + i * 0.75;
      k.num(s, i + 1, { x: rx, y: y + 0.11, d: 0.4, fill: i === 3 ? C.accent2 : C.accent1 });
      k.text(s, t, { x: rx + 0.55, y, w: rw - 0.55, h: 0.62, fontSize: 13, valign: "middle" });
    });
    k.text(s, "그래서 면제 구간 없이 진입 1회, 종료 1회", { x: M, y: 4.85, w: W - 2 * M, h: 0.3, fontSize: 13, bold: true, color: C.accent1 });
    s.addNotes(
      "수수료는 들어갈 때 한 번, 나올 때 한 번입니다. 직접 팔든, 강제로 청산되든, 판이 끝나서 정리되든 똑같이 부과합니다. " +
        "처음에는 판 종료 정리만 면제하는 안도 있었는데, 그러면 끝나기 직전에 파는 사람이 오히려 수수료를 더 내게 됩니다. 그러면 합리적인 플레이어는 손절을 하지 않게 되고, 배율과 청산을 넣어 팔지 말지 고민하게 만든다는 목적이 사라집니다. " +
        "그래서 면제 구간을 두지 않았습니다."
    );
  }

  // ───────────────────────── 7. 잔고 불변식 ─────────────────────────
  {
    const s = content("게임 규칙", "잔고 불변식: 현금은 항상 0 이상", "수수료는 남은 현금 한도까지만 부과, 매도와 청산과 종료 정리 모두 같은 규칙");
    k.card(s, { x: M, y: 1.5, w: 3.6, h: 3.5, fill: C.background2, name: "example" });
    k.badge(s, I.wallet, { x: M + 0.25, y: 1.7, d: 0.55 });
    k.text(s, "극단적인 경우", { x: M + 0.95, y: 1.7, w: 2.4, h: 0.55, fontSize: 15, bold: true, valign: "middle" });
    k.text(s, [
      { text: "현금을 전부 걸고 바로 청산", options: { bold: true, breakLine: true } },
      { text: "증거금 전액 소멸\n청산 수수료가 남은 현금보다 큼", options: { color: C.accent6, breakLine: true } },
      { text: " ", options: { breakLine: true } },
      { text: "결과: 현금 0원", options: { bold: true, color: C.accent1, breakLine: true } },
      { text: "음수로 내려가지 않음, 수익률 하한 −100%", options: { color: C.accent6 } },
    ], { x: M + 0.25, y: 2.5, w: 3.1, h: 2.3, fontSize: 12 });
    const rx = 4.4, rw = W - M - rx;
    const reasons = [
      ["증거금 전액 소멸이 이미 최대 페널티", "그 위에 더 빼는 것은 페널티 강화가 아니라 계산의 부산물"],
      ["음수 자산은 버그처럼 보임", "순위표에 수익률 −107% 같은 값이 표시됨"],
      ["청산돼도 재기 가능해야 함", "남은 시간이 관전이 되지 않도록 증거금 격리 유지"],
      ["통제할 수 없는 차이 제거", "청산 수수료는 명목가치 기준이라 비싼 종목일수록 불리"],
    ];
    reasons.forEach(([t, d], i) => {
      const y = 1.5 + i * 0.89;
      k.card(s, { x: rx, y, w: rw, h: 0.78, name: `reason-${i + 1}` });
      k.text(s, t, { x: rx + 0.2, y: y + 0.08, w: rw - 0.4, h: 0.3, fontSize: 13, bold: true });
      k.text(s, d, { x: rx + 0.2, y: y + 0.4, w: rw - 0.4, h: 0.3, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "현금은 어떤 경우에도 0 아래로 내려가지 않습니다. 수수료는 남은 현금 한도까지만 부과합니다. 현금을 전부 걸었다가 바로 청산되는 극단적인 경우에도 현금은 0원에서 멈춥니다. " +
        "이유는 네 가지입니다. 증거금이 전부 사라지는 것 자체가 이미 최대 페널티이고, 자산이 음수가 되면 순위표에 마이너스 107% 같은 값이 떠서 버그처럼 보입니다. " +
        "또 청산돼도 남은 현금으로 다시 도전할 수 있어야 하고, 청산 수수료는 비싼 종목일수록 커서 플레이어가 통제할 수 없는 차이를 만듭니다. 이건 플레이어 특혜가 아니라 없는 돈을 빼지 않는다는 원칙입니다."
    );
  }

  // ───────────────────────── 8. 실제 급락 구간 재생 ─────────────────────────
  pres.addSection({ title: "규칙 검증" });
  {
    const s = content("규칙 검증", "실제 급락 구간 재생: 배율별 결과", "에코프로비엠 일봉 2024-01-11 ~ 2025-01-07 (시작 대비 최저 −65.5%), 시드 100만 원, 증거금 상한 50만 원 동일");
    // 배율별 진행 막대 — 240틱 가로축
    const gx = M + 0.75, gw = 4.55, tAt = (t) => gx + (t / 240) * gw;
    const lanes = [
      ["×3", 86, "86틱 청산", C.accent2],
      ["×2", 163, "163틱 청산", C.accent2],
      ["×1", 240, "끝까지 생존", C.accent5],
    ];
    lanes.forEach(([lv, t, label, color], i) => {
      const y = 1.75 + i * 0.75;
      k.text(s, lv, { x: M, y, w: 0.6, h: 0.42, fontSize: 15, bold: true, valign: "middle" });
      k.rect(s, { x: gx, y: y + 0.09, w: gw, h: 0.24, fill: { color: C.background2 } });
      k.rect(s, { x: gx, y: y + 0.09, w: tAt(t) - gx, h: 0.24, fill: { color: C.accent1 } });
      if (t < 240) s.addShape(S.DIAMOND, { x: tAt(t) - 0.13, y: y + 0.08, w: 0.26, h: 0.26, fill: { color: C.accent4 }, line: { color: C.background1, width: 1 } });
      k.text(s, label, { x: Math.min(tAt(t) + 0.15, gx + gw - 1.3), y: y + 0.35, w: 1.3, h: 0.25, fontSize: 10, bold: true, color });
    });
    [0, 60, 120, 180, 240].forEach((t) => k.text(s, `${t}틱`, { x: tAt(t) - 0.3, y: 3.95, w: 0.6, h: 0.22, fontSize: 9, color: C.accent6, align: "center" }));
    k.card(s, { x: M, y: 4.3, w: 5.3, h: 0.72, fill: C.background2, name: "insight" });
    k.text(s, [
      { text: "배율 3이 배율 2보다 덜 잃음", options: { bold: true, breakLine: true } },
      { text: "정수 수량 때문에 배율 3은 4주(증거금 약 40만 원), 배율 2는 3주(약 45만 원)", options: { fontSize: 11, color: C.accent6 } },
    ], { x: M + 0.2, y: 4.36, w: 4.9, h: 0.6, fontSize: 13 });
    const rx = 6.1, rw = W - M - rx;
    k.text(s, "최종 순위", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 13, bold: true });
    const ranks = [
      ["1", "관망 (매수 안 함)", "1,000,000", "0%"],
      ["2", "배율 ×1", "812,360", "−18.8%"],
      ["3", "배율 ×3", "595,396", "−40.5%"],
      ["4", "배율 ×2", "546,179", "−45.4%"],
    ];
    ranks.forEach(([r, who, asset, ret], i) => {
      const y = 1.9 + i * 0.78;
      const odd = i === 2 || i === 3;
      k.card(s, { x: rx, y, w: rw, h: 0.66, fill: odd ? C.background2 : C.background1, line: odd ? undefined : C.background2, name: `rank-${r}` });
      k.text(s, r, { x: rx + 0.15, y, w: 0.35, h: 0.66, fontSize: 16, bold: true, color: r === "1" ? C.accent4 : C.accent6, valign: "middle" });
      k.text(s, who, { x: rx + 0.5, y, w: 1.65, h: 0.66, fontSize: 12, bold: true, valign: "middle" });
      k.text(s, [
        { text: asset, options: { bold: true, breakLine: true } },
        { text: ret, options: { fontSize: 10, color: ret === "0%" ? C.accent6 : C.accent3 } },
      ], { x: rx + 2.0, y: y + 0.06, w: rw - 2.15, h: 0.56, fontSize: 12, align: "right", valign: "middle" });
    });
    s.addNotes(
      "규칙이 실제 시세에서 어떻게 동작하는지, 에코프로비엠이 1년 동안 최대 65% 빠진 구간으로 직접 돌려 봤습니다. 네 명이 같은 시드에서 증거금 상한 50만 원으로 같은 종목을 샀습니다. " +
        "배율 3은 86틱에서, 배율 2는 163틱에서 청산됐고 배율 1은 끝까지 버텼습니다. 그런데 최종 순위를 보면 배율 3이 배율 2보다 덜 잃었습니다. " +
        "수량이 정수라 배율 3은 4주, 배율 2는 3주를 사게 되는데, 그 결과 실제로 건 증거금이 배율 2 쪽이 더 컸기 때문입니다. 이 결과가 다음 장의 시드머니 결정으로 이어졌습니다."
    );
  }

  // ───────────────────────── 9. 시드머니 1억 원 ─────────────────────────
  {
    const s = content("규칙 검증", "시드머니 1억 원의 근거", "수량은 정수, 시드가 작으면 결과가 수량 반올림에 좌우");
    const stats = [
      ["156만 ~ 191만 원", "SK하이닉스 1주 가격 (분봉 구간)", "일봉 최고 296만 원\n시드 100만 원이면 배율 1로도 매수 불가"],
      ["4주 vs 3주", "앞 장의 순위 역전", "배율보다 수량 반올림이 결과를 결정"],
      ["30주 이상", "시드 1억 원일 때 가장 비싼 종목도", "수량 반올림 영향 1 ~ 3%로 축소"],
    ];
    stats.forEach(([v, l, d], i) => {
      const y = 1.5 + i * 1.18;
      k.card(s, { x: M, y, w: 5.6, h: 1.05, name: `seed-${i + 1}` });
      k.text(s, v, { x: M + 0.25, y, w: 2.55, h: 1.05, fontSize: 22, bold: true, color: i === 2 ? C.accent1 : C.text1, valign: "middle" });
      k.text(s, [
        { text: l, options: { bold: true, breakLine: true } },
        { text: d, options: { fontSize: 11, color: C.accent6 } },
      ], { x: M + 2.8, y: y + 0.12, w: 2.65, h: 0.85, fontSize: 12, valign: "middle" });
    });
    const rx = 6.4, rw = W - M - rx;
    k.card(s, { x: rx, y: 1.5, w: rw, h: 3.5, fill: C.background2, name: "rejected" });
    k.text(s, "검토 후 기각한 대안", { x: rx + 0.22, y: 1.65, w: rw - 0.44, h: 0.3, fontSize: 13, bold: true });
    k.text(s, [
      { text: "종목별 가격 정규화", options: { bold: true, breakLine: true } },
      { text: "실제 가격 감각이 사라짐", options: { fontSize: 11, color: C.accent6, breakLine: true } },
      { text: " ", options: { fontSize: 6, breakLine: true } },
      { text: "소수 수량 허용", options: { bold: true, breakLine: true } },
      { text: "정수 수량 원칙 변경 필요\n청산가 계산 규칙 전체 재검토", options: { fontSize: 11, color: C.accent6, breakLine: true } },
      { text: " ", options: { fontSize: 6, breakLine: true } },
      { text: "결정", options: { bold: true, color: C.accent1, breakLine: true } },
      { text: "시드 1억 원, 방마다 변경 가능\n플레이하며 재검토", options: { fontSize: 11, color: C.accent6 } },
    ], { x: rx + 0.22, y: 2.05, w: rw - 0.44, h: 2.85, fontSize: 13 });
    s.addNotes(
      "수량이 정수라서 주가에 비해 시드가 작으면 문제가 생깁니다. SK하이닉스는 1주에 150만 원이 넘어서 시드가 100만 원이면 배율 1로는 아예 살 수 없습니다. " +
        "또 앞 장처럼 몇 주 단위로만 움직이면 배율보다 수량 반올림이 결과를 결정해 버립니다. 시드를 1억 원으로 하면 가장 비싼 종목도 30주 이상 살 수 있어서 반올림 영향이 1에서 3% 수준으로 줄어듭니다. " +
        "종목별로 가격을 정규화하거나 소수 수량을 허용하는 방법도 검토했지만, 실제 가격 감각이 사라지거나 계산 규칙 전체를 바꿔야 해서 시드를 키우는 쪽을 택했습니다."
    );
  }

  // ───────────────────────── 10. 순위와 주문 거부 ─────────────────────────
  {
    const s = content("규칙 검증", "순위와 주문 거부 규칙", "한 판 240틱, 시세는 종목마다 241봉 (0번째 = 시작가)");
    k.card(s, { x: M, y: 1.5, w: 4.3, h: 3.5, fill: C.background2, name: "ranking" });
    k.badge(s, I.rank, { x: M + 0.25, y: 1.7, d: 0.55 });
    k.text(s, "순위", { x: M + 0.95, y: 1.7, w: 2.5, h: 0.55, fontSize: 16, bold: true, valign: "middle" });
    k.text(s, [
      { text: "기준", options: { bold: true, breakLine: true } },
      { text: "종료 정리 후 총자산(= 현금) 내림차순", options: { color: C.accent6, breakLine: true } },
      { text: " ", options: { fontSize: 6, breakLine: true } },
      { text: "동점은 공동 순위", options: { bold: true, breakLine: true } },
      { text: "1위, 1위, 3위 순으로 다음 순위를 건너뜀\n거래하지 않은 참가자끼리 시드 그대로 동점 실제 발생\n동점자는 입장 순서대로 나열", options: { color: C.accent6 } },
    ], { x: M + 0.25, y: 2.45, w: 3.8, h: 2.4, fontSize: 12 });
    const rx = 5.1, rw = W - M - rx;
    k.text(s, "주문 거부 조건", { x: rx, y: 1.5, w: rw, h: 0.3, fontSize: 13, bold: true });
    const rejects = [
      ["현금 부족", "실제 증거금 + 수수료가 현금보다 큼"],
      ["증거금 부족", "체결 수량 0 (1주 값에 못 미침)"],
      ["허용되지 않은 배율", "일봉 1, 2, 3과 분봉 1, 3, 5, 10 외"],
      ["같은 종목 보유 중", "종목당 포지션 1개, 평단가 계산 회피"],
      ["시간 종료", "마지막 틱 이후 주문은 종료 정리로만"],
    ];
    rejects.forEach(([t, d], i) => {
      const y = 1.9 + i * 0.62;
      k.card(s, { x: rx, y, w: rw, h: 0.54, name: `reject-${i + 1}` });
      k.text(s, t, { x: rx + 0.2, y, w: 1.75, h: 0.54, fontSize: 12, bold: true, valign: "middle" });
      k.text(s, d, { x: rx + 1.95, y, w: rw - 2.1, h: 0.54, fontSize: 11, color: C.accent6, valign: "middle" });
    });
    s.addNotes(
      "순위는 판이 끝나고 남은 포지션을 모두 정리한 뒤의 자산으로 매깁니다. 자산이 같으면 공동 순위입니다. 아무 거래도 하지 않은 참가자끼리는 시드 그대로라 동점이 실제로 나옵니다. " +
        "주문이 거부되는 경우는 다섯 가지입니다. 현금이 모자라거나, 증거금이 1주 값에 못 미치거나, 허용되지 않은 배율이거나, 같은 종목을 이미 갖고 있거나, 시간이 끝난 경우입니다. 종목당 포지션을 하나로 제한한 것은 평단가와 부분 청산 계산을 피하기 위해서입니다."
    );
  }

  // ───────────────────────── 11. 동시 진행 ─────────────────────────
  pres.addSection({ title: "서버" });
  {
    const s = content("서버", "동시 진행: 방마다 한 줄 처리", "틱, 주문, 입장, 준비를 방마다 하나의 줄에서 도착 순서대로 처리");
    const rooms = [["방 A", ["틱 15", "주문", "주문", "틱 16", "주문"]], ["방 B", ["입장", "준비", "틱 1", "주문", "틱 2"]]];
    rooms.forEach(([name, items], r) => {
      const y = 1.6 + r * 1.2;
      k.text(s, name, { x: M, y, w: 0.8, h: 0.6, fontSize: 14, bold: true, valign: "middle" });
      items.forEach((t, i) => {
        const x = M + 0.85 + i * 0.82;
        const tick = t.startsWith("틱");
        k.card(s, { x, y: y + 0.06, w: 0.72, h: 0.48, fill: tick ? C.accent1 : C.background2, name: `q-${r}-${i}` });
        k.text(s, t, { x, y: y + 0.06, w: 0.72, h: 0.48, fontSize: 10, bold: true, align: "center", valign: "middle", color: tick ? C.background1 : C.text1 });
      });
      k.arrow(s, M + 0.85 + 5 * 0.82 + 0.02, y + 0.3, M + 0.85 + 5 * 0.82 + 0.35, y + 0.3);
      k.card(s, { x: M + 5.35, y: y + 0.02, w: 0.65, h: 0.56, fill: C.text1, name: `worker-${r}` });
      k.text(s, "처리", { x: M + 5.35, y: y + 0.02, w: 0.65, h: 0.56, fontSize: 11, bold: true, color: C.background1, align: "center", valign: "middle" });
    });
    k.text(s, "방끼리는 독립, 서로 기다리지 않음", { x: M, y: 3.95, w: 6.0, h: 0.3, fontSize: 11, color: C.accent6 });
    const rx = 6.85, rw = W - M - rx;
    const pts = [
      ["순서 섞임 없음", "동시 주문도 한 줄로 정렬\n잠금 장치 불필요"],
      ["게임 중 저장 없음", "판 시작 시 시세 1회 읽기\n종료 시 결과 1회 저장"],
      ["종료 시 한 번에 저장", "방, 참가자, 체결 내역을 한 번에\n실패해도 게임은 정상 종료"],
    ];
    pts.forEach(([t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.card(s, { x: rx, y, w: rw, h: 1.05, name: `server-${i + 1}` });
      k.text(s, t, { x: rx + 0.18, y: y + 0.1, w: rw - 0.36, h: 0.3, fontSize: 13, bold: true });
      k.text(s, d, { x: rx + 0.18, y: y + 0.42, w: rw - 0.36, h: 0.58, fontSize: 10.5, color: C.accent6 });
    });
    k.card(s, { x: M, y: 4.35, w: 6.0, h: 0.65, fill: C.background2, name: "verified" });
    k.text(s, [
      { text: "확인: ", options: { bold: true, color: C.accent1 } },
      { text: "방 두 개 동시 240틱 진행, 서로 간섭 없음", options: {} },
    ], { x: M + 0.2, y: 4.35, w: 5.6, h: 0.65, fontSize: 11, valign: "middle" });
    s.addNotes(
      "여러 명이 동시에 주문해도 결과가 공정하려면 처리 순서가 섞이면 안 됩니다. 그래서 방마다 처리 줄을 하나씩 두고, 틱과 주문과 입장을 모두 그 줄에서 도착한 순서대로 처리합니다. " +
        "한 줄로만 처리하니 잠금 같은 장치가 필요 없고, 방끼리는 서로 독립이라 다른 방을 기다리지 않습니다. " +
        "또 게임 중에는 데이터베이스에 쓰지 않습니다. 시작할 때 시세를 한 번 읽고, 끝날 때 결과를 한 번에 저장합니다. 저장에 실패하더라도 게임 자체는 정상적으로 끝납니다. 방 두 개를 동시에 돌려 서로 간섭하지 않는 것을 확인했습니다."
    );
  }

  // ───────────────────────── 12. 실시간 전달 ─────────────────────────
  {
    const s = content("서버", "실시간 전달: 무엇을, 누구에게, 언제", "1틱 = 1초, 같은 방 참가자는 같은 틱을 동시에 수신");
    const hdr = (t) => ({ text: t, options: { bold: true, color: HEX.lt1, fill: { color: HEX.accent1 }, align: "center" } });
    const cell = (t, o = {}) => ({ text: t, options: { align: "center", ...o } });
    s.addTable(
      [
        [hdr("내용"), hdr("받는 사람"), hdr("시점")],
        [cell("시세 (종목별 현재가, 캔들)", { align: "left" }), cell("전원"), cell("1초마다")],
        [cell("내 현금, 포지션, 총자산", { align: "left" }), cell("본인만", { bold: true }), cell("1초마다")],
        [cell("주문 결과 (체결 또는 거부 사유)", { align: "left" }), cell("본인만", { bold: true }), cell("주문 직후")],
        [cell("순위 (남의 포지션, 배율 포함)", { align: "left" }), cell("전원"), cell("5초마다")],
        [cell("청산 알림", { align: "left" }), cell("전원"), cell("발생 즉시")],
        [cell("최종 순위", { align: "left" }), cell("전원"), cell("종료 시")],
      ],
      { x: M, y: 1.5, w: 5.6, colW: [3.0, 1.3, 1.3], rowH: 0.42, fontSize: 12, color: HEX.dk1, border: { type: "solid", pt: 0.75, color: "D9DCE3" }, valign: "middle" }
    );
    const rx = 6.4, rw = W - M - rx;
    const pts = [
      ["남의 포지션 공개는 의도된 설계", "누가 크게 걸었는지 보여서 역전 긴장감 발생"],
      ["게임 중 캔들에 날짜 없음", "날짜와 가격을 함께 보내면 종목 추정 가능"],
      ["끊겨도 다시 들어오면 이어짐", "현재 틱, 내 상태, 순위를 순서대로 다시 수신"],
    ];
    pts.forEach(([t, d], i) => {
      const y = 1.5 + i * 1.18;
      k.card(s, { x: rx, y, w: rw, h: 1.05, name: `rt-${i + 1}` });
      k.text(s, t, { x: rx + 0.18, y: y + 0.1, w: rw - 0.36, h: 0.3, fontSize: 12.5, bold: true });
      k.text(s, d, { x: rx + 0.18, y: y + 0.45, w: rw - 0.36, h: 0.55, fontSize: 11, color: C.accent6 });
    });
    s.addNotes(
      "실시간으로 무엇을 누구에게 언제 보내는지 정리한 표입니다. 시세는 1초마다 모두에게 같이 보내고, 내 현금과 포지션, 주문 결과는 본인에게만 보냅니다. " +
        "순위는 5초마다 보내는데, 여기에는 다른 사람의 포지션과 배율도 들어갑니다. 누가 크게 걸었는지 보여야 역전의 긴장감이 생기기 때문에 의도적으로 공개했습니다. " +
        "반대로 게임 중 캔들에는 날짜를 넣지 않습니다. 날짜와 가격이 같이 있으면 종목을 바로 알아낼 수 있기 때문입니다. 연결이 끊겨도 다시 들어오면 지금 상태를 순서대로 다시 받아 이어서 할 수 있습니다."
    );
  }

  // ───────────────────────── 13. 검증 ─────────────────────────
  {
    const s = content("서버", "검증: 화면보다 테스트 먼저", "엔진은 화면 없이 테스트로 먼저 증명, 이후 브라우저 두 개로 실제 대전 확인");
    const stats = [
      ["240틱", "화면 없이 한 판 완주", "시작부터 종료, 순위 산출까지 테스트로 실행"],
      ["50여 개", "엔진과 청산 규칙 테스트", "경계값, 잔고 불변식, 공동 순위 등"],
      ["5개 구간", "실제 시세로 재생 검증", "급등, 급락, 횡보 구간 (일봉, 분봉)"],
      ["240 = 240", "두 브라우저가 받은 틱", "같은 방 두 참가자의 틱 240개 일치"],
      ["124틱", "배율 3 청산 발생", "두 화면에 같은 순간 청산 알림"],
      ["200여 개", "전체 자동 테스트", "모두 통과"],
    ];
    const cw = 2.85, ch = 1.6, gap = 0.225;
    stats.forEach(([v, l, d], i) => {
      const x = M + (i % 3) * (cw + gap);
      const y = 1.5 + Math.floor(i / 3) * (ch + 0.2);
      k.card(s, { x, y, w: cw, h: ch, name: `stat-${i + 1}` });
      k.text(s, v, { x: x + 0.22, y: y + 0.15, w: cw - 0.44, h: 0.6, fontSize: 28, bold: true, color: C.accent1 });
      k.text(s, l, { x: x + 0.22, y: y + 0.78, w: cw - 0.44, h: 0.3, fontSize: 13, bold: true });
      k.text(s, d, { x: x + 0.22, y: y + 1.1, w: cw - 0.44, h: 0.42, fontSize: 10.5, color: C.accent6 });
    });
    s.addNotes(
      "검증은 화면보다 테스트를 먼저 했습니다. 게임 엔진은 화면 없이 테스트 안에서 시작부터 240틱, 종료와 순위까지 돌아가는 것을 먼저 확인했습니다. 이 단계가 안 되면 일정이 아니라 설계를 다시 봐야 한다고 정해 두었습니다. " +
        "엔진과 청산 규칙에 대한 테스트가 50여 개 있고, 실제 시세 구간 다섯 개로도 재생해 봤습니다. 그 다음 브라우저 두 개로 같은 방에 들어가서 두 쪽이 받은 틱 240개가 모두 같은 것, 124틱에서 배율 3 청산이 두 화면에 동시에 알려지는 것을 확인했습니다."
    );
  }

  // ───────────────────────── 14. 정리 ─────────────────────────
  pres.addSection({ title: "정리" });
  {
    const s = pres.addSlide({ masterName: "DARK_TOP", sectionTitle: "정리" });
    s.addText("정리", { placeholder: "kicker" });
    s.addText("세 가지 목표, 이렇게 지킴", { placeholder: "title" });
    const items = [
      ["정확", "정수 수량과 증거금 역산, 반올림 방향 명시, 배율 3 경계값 검증"],
      ["공정", "방마다 한 줄 처리, 게임 중 외부 호출 없음, 같은 틱 동시 전달"],
      ["고민 유지", "수수료 면제 없음, 잔고 0 이상 보장, 남의 포지션 공개"],
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
      { text: "다시 볼 값", options: { bold: true, fontSize: 13, color: HEX.accent4, breakLine: true } },
      { text: "수수료율 0.15%, 시드 1억 원,\n배율 상한은 플레이 결과로\n재검토 예정", options: { fontSize: 11, color: HEX.lt2 } },
    ], { x: nx + 0.2, y: 2.0, w: nw - 0.4, h: 1.3 });
    k.card(s, { x: nx, y: 3.6, w: nw, h: 1.3, fill: C.accent1, name: "message" });
    k.text(s, [
      { text: "한 줄 요약", options: { bold: true, fontSize: 12, breakLine: true } },
      { text: "규칙은 숫자로,\n검증은 테스트로", options: { bold: true, fontSize: 18 } },
    ], { x: nx + 0.2, y: 3.72, w: nw - 0.4, h: 1.1, color: C.background1 });
    s.addNotes(
      "정리하겠습니다. 정확성은 정수 수량과 증거금 역산, 대상별 반올림 방향, 배율 3 경계값 테스트로 지켰습니다. 공정성은 방마다 한 줄 처리와 게임 중 외부 호출을 없애는 것으로 지켰습니다. " +
        "그리고 수수료 면제를 두지 않고 잔고가 음수가 되지 않게 해서, 팔지 말지 고민하는 게임의 핵심이 남도록 했습니다. 수수료율과 시드, 배율 상한은 실제 플레이 결과를 보고 다시 검토할 예정입니다. 감사합니다."
    );
  }

  await finish(pres, OUT);
  console.log(`만들었습니다: ${OUT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
