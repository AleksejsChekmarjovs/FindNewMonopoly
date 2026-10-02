"use strict";

const PLAYER_COLORS = ["#e53935", "#1e88e5", "#43a047", "#fdd835", "#8e24aa", "#fb8c00", "#00acc1", "#6d4c41"];
const DICE_FACES = ["", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅"];

const $ = (id) => document.getElementById(id);

let socket;
let me = { playerId: null, roomId: null };
let boardBuilt = false;
/** Состояние, пришедшее во время анимации фишки: покажем, когда она дойдёт. */
let pendingGame = null;
/** Последнее отрисованное состояние — для карточки клетки. */
let detailsGame = null;
/** Кто из игроков сейчас подключён (id). */
let online = new Set();

// ---------------------------------------------------------------- сессия

/**
 * {roomId, playerId, token} — чтобы вернуться в партию после обрыва, перезагрузки или закрытия вкладки.
 * sessionStorage — своя у каждой вкладки (можно играть за двоих в двух вкладках),
 * localStorage — последняя сессия браузера, если вкладку закрыли и открыли заново.
 */
const SESSION_KEY = "monopoly.session";

function saveSession(s) {
    const value = JSON.stringify(s);
    try { sessionStorage.setItem(SESSION_KEY, value); } catch { /* приватный режим и т.п. */ }
    try { localStorage.setItem(SESSION_KEY, value); } catch { /* */ }
}

function loadSession() {
    for (const storage of [() => sessionStorage, () => localStorage]) {
        try {
            const value = storage().getItem(SESSION_KEY);
            if (value) return JSON.parse(value);
        } catch { /* */ }
    }
    return null;
}

function clearSession() {
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* */ }
    try { localStorage.removeItem(SESSION_KEY); } catch { /* */ }
}

// ---------------------------------------------------------------- соединение

const CLOSE_OPENED_ELSEWHERE = 4000;
let reconnectDelay = 1000;
let reconnectTimer = null;

function connect(firstMessage) {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${proto}://${location.host}/ws`);
    socket.onopen = () => send(firstMessage);
    socket.onmessage = (e) => handle(JSON.parse(e.data));
    socket.onclose = (e) => onDisconnect(e.code);
}

function send(msg) {
    if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(msg));
    } else {
        showError("Нет связи с сервером");
    }
}

/** Обрыв: если мы в партии — переподключаемся с нарастающей паузой (1, 2, 4… до 10 с). */
function onDisconnect(code) {
    const session = loadSession();
    if (code === CLOSE_OPENED_ELSEWHERE) {
        showBanner("Игра открыта в другой вкладке или окне. Обновите страницу, чтобы вернуться сюда.");
        return;
    }
    if (!session) {
        showError("Соединение с сервером потеряно");
        return;
    }
    showBanner("Связь потеряна — переподключаемся…");
    clearTimeout(reconnectTimer);
    reconnectTimer = setTimeout(() => resume(session), reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 10_000);
}

function resume(session) {
    connect({ type: "RESUME", roomId: session.roomId, token: session.token });
}

function handle(msg) {
    switch (msg.type) {
        case "WELCOME":
            me = { playerId: msg.playerId, roomId: msg.roomId };
            saveSession({ roomId: msg.roomId, playerId: msg.playerId, token: msg.token });
            reconnectDelay = 1000;
            hideBanner();
            break;
        case "RESUME_FAILED":
            clearSession();
            hideBanner();
            showScreen("lobby-screen");
            showError(msg.message);
            break;
        case "LOBBY":
            online = new Set(msg.online);
            renderLobby(msg);
            break;
        case "STATE":
            online = new Set(msg.online);
            renderGame(msg.game);
            break;
        case "ERROR":
            showError(msg.message);
            break;
    }
}

function showScreen(id) {
    for (const s of ["lobby-screen", "waiting-screen", "game-screen"]) {
        $(s).classList.toggle("hidden", s !== id);
    }
}

function showBanner(text) {
    $("connection").textContent = text;
    $("connection").classList.remove("hidden");
}

function hideBanner() {
    $("connection").classList.add("hidden");
}

/**
 * При загрузке страницы. Сессия этой вкладки (перезагрузка, обрыв) — возвращаемся сразу.
 * Сессия из localStorage может быть чужой: в соседней вкладке человек играет за другого игрока,
 * и автоматический вход вытеснил бы его. Поэтому только предлагаем кнопку.
 */
{
    let own = null;
    try { own = JSON.parse(sessionStorage.getItem(SESSION_KEY)); } catch { /* */ }
    const last = own ? null : loadSession();
    if (own) {
        showBanner("Возвращаемся в партию…");
        resume(own);
    } else if (last) {
        $("resume-room").textContent = last.roomId;
        $("resume-row").classList.remove("hidden");
        $("resume-btn").onclick = () => {
            $("resume-row").classList.add("hidden");
            showBanner("Возвращаемся в партию…");
            resume(last);
        };
    }
}

// ---------------------------------------------------------------- лобби

$("create-btn").onclick = () => {
    const name = $("name-input").value.trim();
    if (!name) return showError("Укажите имя");
    connect({ type: "CREATE", name });
};

$("join-btn").onclick = () => {
    const name = $("name-input").value.trim();
    const roomId = $("room-input").value.trim();
    if (!name || !roomId) return showError("Укажите имя и код комнаты");
    connect({ type: "JOIN", name, roomId });
};

$("start-btn").onclick = () => send({ type: "START" });

function renderLobby(msg) {
    $("lobby-screen").classList.add("hidden");
    $("waiting-screen").classList.remove("hidden");
    $("room-code").textContent = msg.roomId;
    $("waiting-players").replaceChildren(...msg.players.map((p) => {
        const li = document.createElement("li");
        li.textContent = p.name + (p.playerId === me.playerId ? " (вы)" : "")
            + (online.has(p.playerId) ? "" : " — не в сети");
        return li;
    }));
    const isHost = msg.players[0]?.playerId === me.playerId;
    $("start-btn").disabled = !isHost || msg.players.length < 2;
    $("start-btn").textContent = isHost ? "Начать игру" : "Ждём создателя комнаты…";
}

// ---------------------------------------------------------------- игра

/** Индекс клетки 0..39 -> [строка, столбец] в сетке 11x11. Старт — правый нижний угол. */
function gridPos(i) {
    if (i <= 10) return [11, 11 - i];
    if (i <= 20) return [11 - (i - 10), 1];
    if (i <= 30) return [1, 1 + (i - 20)];
    return [1 + (i - 30), 11];
}

function buildBoard(tiles) {
    const board = $("board");
    for (const t of tiles) {
        const el = document.createElement("div");
        el.className = "tile";
        el.id = "tile-" + t.index;
        const [row, col] = gridPos(t.index);
        el.style.gridArea = `${row} / ${col}`;
        if (t.group) {
            const band = document.createElement("div");
            band.className = "band";
            band.style.background = `var(--${t.group.toLowerCase()})`;
            const buildings = document.createElement("div");
            buildings.className = "buildings";
            band.append(buildings);
            el.append(band);
        }
        const name = document.createElement("div");
        name.className = "name";
        name.textContent = t.name;
        el.append(name);
        if (t.price) {
            const price = document.createElement("div");
            price.className = "price";
            price.textContent = "$" + t.price;
            el.append(price);
        }
        el.addEventListener("mouseenter", () => tileHoverStart(t.index));
        el.addEventListener("mouseleave", tileHoverEnd);
        board.append(el);
    }
    // фишки живут в отдельном слое поверх поля — так их можно плавно двигать между клетками
    const layer = document.createElement("div");
    layer.id = "piece-layer";
    board.append(layer);
    boardBuilt = true;
}

function renderGame(g) {
    $("lobby-screen").classList.add("hidden");
    $("waiting-screen").classList.add("hidden");
    $("game-screen").classList.remove("hidden");
    if (!boardBuilt) buildBoard(g.tiles);

    // сразу — только движение фишек и кубики
    renderPieces(g, colorsOf(g));
    $("dice").textContent = g.lastRoll ? DICE_FACES[g.lastRoll.first] + " " + DICE_FACES[g.lastRoll.second] : "";

    // Пока фишка идёт, результат хода (кнопки, карточка, аренда, деньги, журнал) не показываем
    // и с полем взаимодействовать нельзя. Дойдёт — отрисуем самое свежее состояние.
    if (anyPieceWalking()) {
        pendingGame = g;
        document.body.classList.add("animating");
        $("center").inert = true; // и мышь, и клавиатура (Enter на кнопке «Бросить»)
        return;
    }
    renderDetails(g);
}

/** Вызывается, когда фишка закончила путь: если ждали — показать отложенное состояние. */
function onPieceArrived() {
    if (anyPieceWalking() || !pendingGame) return;
    const g = pendingGame;
    pendingGame = null;
    document.body.classList.remove("animating");
    $("center").inert = false;
    renderDetails(g);
}

function colorsOf(g) {
    const colorOf = {};
    g.players.forEach((p, i) => (colorOf[p.id] = PLAYER_COLORS[i]));
    return colorOf;
}

/** Всё, кроме фишек и кубиков: клетки, статус, кнопки, панели, игроки, журнал. */
function renderDetails(g) {
    detailsGame = g;
    const colorOf = colorsOf(g);

    // владельцы и постройки
    for (const t of g.tiles) {
        const el = $("tile-" + t.index);
        const owner = g.owners[t.index];
        el.classList.toggle("owned", !!owner);
        el.classList.toggle("mortgaged", g.mortgaged.includes(t.index));
        el.style.setProperty("--owner", owner ? colorOf[owner] : "transparent");
        const slot = el.querySelector(".buildings");
        if (slot) slot.replaceChildren(...buildingIcons(g.buildings[t.index] || 0));
    }

    // статус и кубики
    const current = g.players.find((p) => p.id === g.currentPlayerId);
    const myTurn = g.currentPlayerId === me.playerId;
    const nameOf = (id) => g.players.find((p) => p.id === id)?.name;
    if (g.phase === "GAME_OVER") {
        $("status").textContent = `Игра окончена. Победил ${nameOf(g.winnerId)}`;
    } else if (g.phase === "AUCTION") {
        $("status").textContent = g.auction.currentBidderId === me.playerId
            ? "Аукцион: ваша очередь"
            : `Аукцион: торгуется ${nameOf(g.auction.currentBidderId)}`;
    } else if (g.phase === "TRADE_OFFER") {
        $("status").textContent = g.trade.toId === me.playerId ? "Вам предлагают обмен"
            : g.trade.fromId === me.playerId ? "Ждём ответа на предложение обмена"
            : `${nameOf(g.trade.fromId)} предлагает обмен игроку ${nameOf(g.trade.toId)}`;
    } else if (g.phase === "PAYING_DEBT") {
        $("status").textContent = g.debt.debtorId === me.playerId
            ? "Вам не хватает денег — расплатитесь с долгом"
            : `${nameOf(g.debt.debtorId)} расплачивается с долгом`;
    } else {
        $("status").textContent = myTurn ? "Ваш ход" : `Ходит ${current.name}`;
    }

    // последняя вытянутая карточка
    const card = $("card");
    card.classList.toggle("hidden", !g.lastCard);
    if (g.lastCard) {
        card.className = g.lastCard.deck.toLowerCase();
        card.querySelector(".card-deck").textContent =
            g.lastCard.deck === "CHANCE" ? "Шанс" : "Общественная казна";
        card.querySelector(".card-text").textContent = g.lastCard.text;
    }

    renderDebt(g, nameOf);
    renderTrade(g, nameOf);
    startTimers(g, nameOf);

    // аукцион
    const a = g.auction;
    $("auction").classList.toggle("hidden", !a);
    for (const t of g.tiles) {
        $("tile-" + t.index).classList.toggle("auctioned", !!a && a.tileIndex === t.index);
    }
    if (a) {
        const tile = g.tiles[a.tileIndex];
        $("auction-title").textContent = `Аукцион: ${tile.name} (цена $${tile.price})`;
        $("auction-bid").textContent = a.highestBidderId
            ? `Ставка: $${a.highestBid} — ${nameOf(a.highestBidderId)}`
            : "Ставок пока нет";
        $("auction-bidders").textContent = "Участвуют: " + a.bidders.map(nameOf).join(", ");
        const myBid = a.currentBidderId === me.playerId;
        $("auction-controls").classList.toggle("hidden", !myBid);
        if (myBid) {
            const money = g.players.find((p) => p.id === me.playerId).money;
            const input = $("bid-input");
            input.min = a.highestBid + 1;
            input.max = money;
            if (!input.value || +input.value <= a.highestBid) input.value = Math.min(a.highestBid + 10, money);
            for (const btn of document.querySelectorAll("#auction-controls [data-step]")) {
                btn.disabled = a.highestBid + +btn.dataset.step > money;
            }
        }
    } else {
        $("bid-input").value = "";
    }

    // доступные действия
    const mePlayer = g.players.find((p) => p.id === me.playerId);
    const allowed = {
        ROLL: g.phase === "WAITING_FOR_ROLL",
        BUY: g.phase === "AWAITING_BUY_DECISION",
        DECLINE: g.phase === "AWAITING_BUY_DECISION",
        PAY_JAIL_FINE: g.phase === "WAITING_FOR_ROLL" && mePlayer?.inJail,
        USE_JAIL_CARD: g.phase === "WAITING_FOR_ROLL" && mePlayer?.inJail && mePlayer.jailFreeCards > 0,
        END_TURN: g.phase === "TURN_END",
    };
    for (const btn of document.querySelectorAll("#actions button[data-cmd]")) {
        const ok = myTurn && allowed[btn.dataset.cmd];
        btn.classList.toggle("hidden", !ok);
    }

    renderPropertyPanel(g, myTurn);

    // игроки
    $("players").replaceChildren(...g.players.map((p) => {
        const tr = document.createElement("tr");
        tr.classList.toggle("current", p.id === g.currentPlayerId);
        tr.classList.toggle("bankrupt", p.bankrupt);
        const dot = `<span class="token" style="display:inline-block;background:${colorOf[p.id]}"></span>`;
        const notes = [
            p.inJail ? "в тюрьме" : "",
            p.jailFreeCards ? `🔑×${p.jailFreeCards}` : "",
            !p.bankrupt && !online.has(p.id) ? "не в сети" : "",
        ].filter(Boolean).join(" ");
        tr.innerHTML = `<td>${dot}</td><td></td><td>$${p.money}</td><td>${notes}</td>`;
        tr.children[1].textContent = p.name + (p.id === me.playerId ? " (вы)" : "");
        return tr;
    }));

    // журнал
    $("log").replaceChildren(...g.log.map((line) => {
        const li = document.createElement("li");
        li.textContent = line;
        return li;
    }));
    $("log").scrollTop = $("log").scrollHeight;
}

for (const btn of document.querySelectorAll("#actions button[data-cmd]")) {
    btn.onclick = () => send({ type: btn.dataset.cmd });
}

// ---------------------------------------------------------------- застройка

const HOTEL = 5;

/** Значки на цветной полосе клетки: 1–4 зелёных дома или один красный отель. */
function buildingIcons(level) {
    if (level === HOTEL) {
        const hotel = document.createElement("span");
        hotel.className = "hotel";
        hotel.title = "Отель";
        return [hotel];
    }
    return Array.from({ length: level }, () => {
        const house = document.createElement("span");
        house.className = "house";
        house.title = "Дом";
        return house;
    });
}

// ---------------------------------------------------------------- карточка клетки

/** Сколько держать мышь на клетке, прежде чем показать карточку. */
const TILE_CARD_DELAY_MS = 1000;
/** Время, чтобы перевести мышь с клетки на карточку, не закрыв её. */
const TILE_CARD_GRACE_MS = 200;

let tileHoverTimer = null;
let tileCardCloseTimer = null;
let tileCardIndex = null;

function tileHoverStart(index) {
    clearTimeout(tileHoverTimer);
    clearTimeout(tileCardCloseTimer);
    if (tileCardIndex === index) return; // карточка этой клетки уже открыта
    tileHoverTimer = setTimeout(() => openTileCard(index), TILE_CARD_DELAY_MS);
}

function tileHoverEnd() {
    clearTimeout(tileHoverTimer);
    // даём время перейти на карточку; если мышь туда не попала — закрываем
    tileCardCloseTimer = setTimeout(closeTileCard, TILE_CARD_GRACE_MS);
}

$("tile-card").addEventListener("mouseenter", () => clearTimeout(tileCardCloseTimer));
$("tile-card").addEventListener("mouseleave", () => {
    tileCardCloseTimer = setTimeout(closeTileCard, TILE_CARD_GRACE_MS);
});
document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeTileCard();
});

function closeTileCard() {
    tileCardIndex = null;
    $("tile-card").classList.add("hidden");
}

function openTileCard(index) {
    const g = detailsGame;
    if (!g) return;
    tileCardIndex = index;
    const card = $("tile-card");
    card.replaceChildren(...tileCardContent(g, g.tiles[index]));
    card.classList.remove("hidden");
    placeTileCard(card, $("tile-" + index));
}

/** Рядом с клеткой: справа, если не влезает — слева; по вертикали — в пределах окна. */
function placeTileCard(card, tileEl) {
    const r = tileEl.getBoundingClientRect();
    const w = card.offsetWidth;
    const h = card.offsetHeight;
    const gap = 8;
    let left = r.right + gap;
    if (left + w > window.innerWidth - gap) left = r.left - w - gap;
    left = Math.max(gap, Math.min(left, window.innerWidth - w - gap));
    let top = r.top + r.height / 2 - h / 2;
    top = Math.max(gap, Math.min(top, window.innerHeight - h - gap));
    card.style.left = `${left}px`;
    card.style.top = `${top}px`;
}

const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
};

/** Таблица «название — сумма»; строка с current=true подсвечивается (действует сейчас). */
function priceTable(rows) {
    const table = el("table", "tile-card-table");
    for (const { label, value, current } of rows) {
        const tr = el("tr", current ? "current" : "");
        tr.append(el("td", "", label), el("td", "", value));
        table.append(tr);
    }
    return table;
}

function tileCardContent(g, t) {
    const nameOf = (id) => g.players.find((p) => p.id === id)?.name;
    const parts = [];

    const head = el("div", "tile-card-head");
    if (t.group) head.style.background = `var(--${t.group.toLowerCase()})`;
    head.classList.toggle("plain", !t.group);
    head.append(el("div", "tile-card-title", t.name));
    if (t.price) head.append(el("div", "tile-card-price", `Цена $${t.price}`));
    parts.push(head);

    const body = el("div", "tile-card-body");
    parts.push(body);

    if (!t.type || !["PROPERTY", "RAILROAD", "UTILITY"].includes(t.type)) {
        body.append(el("p", "tile-card-text", specialTileText(t)));
        return parts;
    }

    const ownerId = g.owners[t.index];
    const level = g.buildings[t.index] || 0;
    const mortgaged = g.mortgaged.includes(t.index);
    const ownedByOwner = (type) => g.tiles.filter((s) => s.type === type && g.owners[s.index] === ownerId).length;

    if (t.type === "PROPERTY") {
        const group = g.tiles.filter((s) => s.group === t.group);
        const fullGroup = !!ownerId && group.every((s) => g.owners[s.index] === ownerId);
        const houseCost = g.houseCosts[t.group];
        const r = t.rent;
        body.append(el("div", "tile-card-section", "Аренда"));
        body.append(priceTable([
            { label: "Без построек", value: `$${r[0]}`, current: !!ownerId && !fullGroup && level === 0 },
            { label: "Вся группа цвета", value: `$${r[0] * 2}`, current: fullGroup && level === 0 },
            { label: "1 дом", value: `$${r[1]}`, current: level === 1 },
            { label: "2 дома", value: `$${r[2]}`, current: level === 2 },
            { label: "3 дома", value: `$${r[3]}`, current: level === 3 },
            { label: "4 дома", value: `$${r[4]}`, current: level === 4 },
            { label: "Отель", value: `$${r[5]}`, current: level === 5 },
        ]));
        body.append(el("div", "tile-card-section", "Застройка"));
        body.append(priceTable([
            { label: "Дом", value: `$${houseCost}` },
            { label: "Отель", value: `$${houseCost} + 4 дома` },
            { label: "Продажа дома банку", value: `$${houseCost / 2}` },
        ]));
    } else if (t.type === "RAILROAD") {
        const n = ownerId ? ownedByOwner("RAILROAD") : 0;
        body.append(el("div", "tile-card-section", "Аренда — сколько дорог у владельца"));
        body.append(priceTable([1, 2, 3, 4].map((k) => ({
            label: k === 1 ? "1 дорога" : `${k} дороги`, value: `$${25 * 2 ** (k - 1)}`, current: n === k,
        }))));
    } else {
        const n = ownerId ? ownedByOwner("UTILITY") : 0;
        body.append(el("div", "tile-card-section", "Аренда — от суммы кубиков"));
        body.append(priceTable([
            { label: "Одно предприятие", value: "4 × кубики", current: n === 1 },
            { label: "Оба предприятия", value: "10 × кубики", current: n === 2 },
        ]));
    }

    // залог: половина цены, выкуп — залог + 10% с округлением вверх (как на сервере)
    const value = t.price / 2;
    body.append(el("div", "tile-card-section", "Залог"));
    body.append(priceTable([
        { label: "Заложить", value: `$${value}` },
        { label: "Выкупить", value: `$${Math.ceil(value * 1.1)}` },
    ]));

    // с заложенной клетки аренда не берётся — «действующей» строки нет
    if (mortgaged) {
        body.querySelectorAll("tr.current").forEach((tr) => tr.classList.remove("current"));
    }

    // имя владельца — жирным и его цветом (как его фишка)
    const statusEl = el("div", "tile-card-status");
    if (ownerId) {
        const owner = el("span", "tile-card-owner", nameOf(ownerId) + (ownerId === me.playerId ? " (вы)" : ""));
        owner.style.color = colorsOf(g)[ownerId];
        statusEl.append("Владелец: ", owner);
    } else {
        statusEl.append("Свободна — у банка");
    }
    const extra = [];
    if (level === 5) extra.push("Стоит отель");
    else if (level > 0) extra.push(`Домов: ${level}`);
    if (mortgaged) extra.push("Заложена — аренда не платится");
    if (extra.length) statusEl.append(" · " + extra.join(" · "));
    body.append(statusEl);
    return parts;
}

function specialTileText(t) {
    switch (t.type) {
        case "GO": return "Каждый раз, проходя или попадая на «Старт», игрок получает $200.";
        case "TAX": return `Попавший на клетку платит банку $${t.taxAmount}.`;
        case "CHANCE": return "Возьмите верхнюю карточку «Шанс» и выполните написанное.";
        case "COMMUNITY_CHEST": return "Возьмите верхнюю карточку «Общественная казна» и выполните написанное.";
        case "JAIL": return "Просто в гостях — ничего не происходит. Из тюрьмы выходят: дубль, штраф $50 "
            + "или карточка «Освободиться из тюрьмы»; после третьей неудачной попытки штраф обязателен.";
        case "GO_TO_JAIL": return "Отправляйтесь прямо в тюрьму. «Старт» не проходите, $200 не получаете.";
        case "FREE_PARKING": return "Бесплатная стоянка — просто отдых, ничего не происходит.";
        default: return "";
    }
}

// ---------------------------------------------------------------- фишки

const BOARD_SIZE = 40;
/** Один «шаг» фишки по клетке. */
const STEP_MS = 180;
/** Длинный путь (карточка «на Старт») идёт быстрее, чтобы не ждать 7 секунд. */
const MAX_WALK_MS = 2800;

/** id игрока -> { el, shown: клетка, где фишка нарисована сейчас, target, inJail, timer } */
const pieces = new Map();
let piecesGame = null;

function renderPieces(g, colorOf) {
    piecesGame = g;
    for (const p of g.players) {
        let piece = pieces.get(p.id);
        if (!piece) {
            const el = document.createElement("div");
            el.className = "piece";
            el.style.background = colorOf[p.id];
            el.title = p.name;
            $("piece-layer").append(el);
            // первое появление (старт или возвращение в партию) — сразу на место, без анимации
            piece = { el, shown: p.position, target: p.position, inJail: p.inJail, timer: null };
            pieces.set(p.id, piece);
        }
        piece.el.classList.toggle("hidden", p.bankrupt);
        piece.el.classList.toggle("current", p.id === g.currentPlayerId && g.phase !== "GAME_OVER");
        if (p.position !== piece.target) {
            // идём от клетки, где фишка нарисована сейчас (могла не дойти прошлый путь)
            walk(piece, walkPath(piece.shown, p.position, g, !piece.inJail && p.inJail));
            piece.target = p.position;
        }
        piece.inJail = p.inJail;
    }
    layoutPieces();
}

/** Путь по клеткам: [{to, back?, fly?}]. */
function walkPath(from, to, g, wentToJail) {
    const steps = [];
    let at = from;
    const forwardTo = (target) => {
        while (at !== target) {
            at = (at + 1) % BOARD_SIZE;
            steps.push({ to: at });
        }
    };
    if (wentToJail) {
        // Броском дошли до «Отправляйтесь в тюрьму» или до «Шанса»/«Казны» с такой карточкой —
        // сначала показываем путь туда, потом перелёт в тюрьму. Три дубля — перелёт прямо с места.
        const r = g.lastRoll;
        const via = r ? (from + r.first + r.second) % BOARD_SIZE : -1;
        const viaType = via >= 0 ? g.tiles[via].type : null;
        const byCard = g.lastCard?.kind === "GO_TO_JAIL" && (viaType === "CHANCE" || viaType === "COMMUNITY_CHEST");
        if (viaType === "GO_TO_JAIL" || byCard) forwardTo(via);
        steps.push({ to, fly: true });
        return steps;
    }
    if (g.lastCard?.kind === "MOVE_BACK") {
        // дошли до «Шанса», а карточка вернула на 3 клетки назад
        forwardTo((to + 3) % BOARD_SIZE);
        while (at !== to) {
            at = (at - 1 + BOARD_SIZE) % BOARD_SIZE;
            steps.push({ to: at, back: true });
        }
        return steps;
    }
    forwardTo(to);
    return steps;
}

function anyPieceWalking() {
    for (const piece of pieces.values()) {
        if (piece.timer) return true;
    }
    return false;
}

/** Перелёт в тюрьму через поле и пауза на клетке перед ним — чтобы было видно, откуда отправили. */
const FLY_MS = 1000;
const BEFORE_FLY_MS = 400;

function walk(piece, path) {
    clearTimeout(piece.timer);
    const walkSteps = path.filter((s) => !s.fly).length;
    const stepMs = Math.min(STEP_MS, MAX_WALK_MS / Math.max(1, walkSteps));
    const next = () => {
        const step = path.shift();
        if (!step) {
            piece.timer = null;
            piece.el.classList.remove("hop", "fly"); // вернуть пульсацию текущей фишки
            piece.el.style.transitionTimingFunction = "";
            layoutPieces();
            onPieceArrived();
            return;
        }
        piece.shown = step.to;
        const ms = step.fly ? FLY_MS : stepMs;
        piece.el.style.transitionDuration = `${ms}ms`;
        piece.el.style.transitionTimingFunction = step.fly ? "ease-in-out" : "";
        // на каждую клетку — «прыжок», будто фишка отсчитывает шаги; в тюрьму — перелёт через поле
        piece.el.classList.remove("hop", "fly");
        void piece.el.offsetWidth; // перезапуск CSS-анимации
        piece.el.classList.add(step.fly ? "fly" : "hop");
        piece.el.style.setProperty("--step-ms", `${ms}ms`);
        layoutPieces();
        const pause = path[0]?.fly && !step.fly ? BEFORE_FLY_MS : 0;
        piece.timer = setTimeout(next, ms + pause);
    };
    // если перелёт сразу (три дубля) — тоже короткая пауза на месте
    if (path[0]?.fly) {
        piece.timer = setTimeout(next, BEFORE_FLY_MS);
    } else {
        next();
    }
}

/**
 * Расставить фишки по клеткам. Фишка игрока, чей ход, — крупная и в центре клетки,
 * остальные на той же клетке — мелкие, по углам.
 */
function layoutPieces() {
    const g = piecesGame;
    if (!g) return;
    const SLOTS = [[-0.28, 0.28], [0.28, 0.28], [-0.28, -0.02], [0.28, -0.02], [0, 0.34], [0, -0.1], [-0.3, 0.12], [0.3, 0.12]];
    const byTile = new Map();
    for (const p of g.players) {
        const piece = pieces.get(p.id);
        if (!piece || p.bankrupt) continue;
        if (!byTile.has(piece.shown)) byTile.set(piece.shown, []);
        byTile.get(piece.shown).push({ p, piece });
    }
    for (const [tileIndex, list] of byTile) {
        const tile = $("tile-" + tileIndex);
        const w = tile.offsetWidth;
        const h = tile.offsetHeight;
        const cx = tile.offsetLeft + w / 2;
        const cy = tile.offsetTop + h / 2;
        const s = Math.min(w, h);
        let slot = 0;
        for (const { p, piece } of list) {
            const isCurrent = p.id === g.currentPlayerId && g.phase !== "GAME_OVER";
            const size = isCurrent ? Math.max(16, s * 0.6) : Math.max(9, s * 0.3);
            let x = cx;
            let y = cy;
            // пока фишка идёт — по центру клеток, чтобы путь был ровным; на месте — по своим углам
            if (!isCurrent && !piece.timer) {
                const [fx, fy] = SLOTS[slot++ % SLOTS.length];
                x += fx * w;
                y += fy * h;
            }
            Object.assign(piece.el.style, {
                left: `${x}px`, top: `${y}px`, width: `${size}px`, height: `${size}px`,
            });
        }
    }
}

window.addEventListener("resize", layoutPieces);

// ---------------------------------------------------------------- таймеры

/**
 * Сервер присылает, сколько осталось на момент отправки; дальше отсчитываем сами —
 * так не важно, что часы у клиента и сервера расходятся.
 */
let timerState = null;

function startTimers(g, nameOf) {
    timerState = { t: g.timers, receivedAt: performance.now(), g, nameOf };
    const left = g.timers.tradesLeft;
    $("trades-left").textContent = $("trade-open-btn").classList.contains("hidden") ? ""
        : `осталось предложений: ${left}`;
    $("trade-open-btn").disabled = left === 0;
    drawTimers();
}

function formatTime(ms) {
    const s = Math.max(0, Math.ceil(ms / 1000));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

function drawTimers() {
    if (!timerState) return;
    const { t, receivedAt, g, nameOf } = timerState;
    const el = $("timer");
    if (g.phase === "GAME_OVER") {
        el.textContent = "";
        return;
    }
    const passed = performance.now() - receivedAt;
    const turnLeft = t.turnClockRunning ? t.turnMillisLeft - passed : t.turnMillisLeft;
    const who = g.currentPlayerId === me.playerId ? "Ваш ход" : `Ход ${nameOf(g.currentPlayerId)}`;
    let text = `⏱ ${who}: ${formatTime(turnLeft)}` + (t.turnClockRunning ? "" : " (пауза)");
    let urgent = t.turnClockRunning && turnLeft < 30_000;
    if (t.waitMillisLeft != null) {
        const waitLeft = t.waitMillisLeft - passed;
        const whom = t.waitingForId === me.playerId ? "вы" : nameOf(t.waitingForId);
        const what = g.phase === "TRADE_OFFER" ? "ответ на обмен"
            : g.phase === "AUCTION" ? "ставка" : "расплата с долгом";
        text += ` · ${what} (${whom}): ${formatTime(waitLeft)}`;
        urgent = urgent || waitLeft < 10_000;
    }
    el.textContent = text;
    el.classList.toggle("urgent", urgent);
}

setInterval(drawTimers, 250);

// ---------------------------------------------------------------- обмен

let lastGame = null;
/** Черновик предложения живёт на клиенте, пока окно открыто; новые STATE его не сбрасывают. */
const tradeDraft = { open: false, partnerId: null, give: new Set(), take: new Set() };

function canProposeTrade(g) {
    return g.currentPlayerId === me.playerId
        && ["WAITING_FOR_ROLL", "TURN_END"].includes(g.phase)
        && g.players.some((p) => p.id !== me.playerId && !p.bankrupt);
}

/** Улицу нельзя обменять, пока в её цветовой группе есть постройки. */
function tradable(g, t) {
    return !t.group || !g.tiles.some((s) => s.group === t.group && (g.buildings[s.index] || 0) > 0);
}

function renderTrade(g, nameOf) {
    lastGame = g;
    $("trade-open-btn").classList.toggle("hidden", !canProposeTrade(g));
    if (tradeDraft.open && !canProposeTrade(g)) closeTradeEditor();
    if (tradeDraft.open) renderTradeEditor(g);
    renderTradeOffer(g, nameOf);
}

function renderTradeEditor(g) {
    const partners = g.players.filter((p) => p.id !== me.playerId && !p.bankrupt);
    if (!partners.some((p) => p.id === tradeDraft.partnerId)) {
        tradeDraft.partnerId = partners[0].id;
        tradeDraft.take.clear();
    }
    $("trade-partner").replaceChildren(...partners.map((p) => {
        const o = document.createElement("option");
        o.value = p.id;
        o.textContent = p.name;
        o.selected = p.id === tradeDraft.partnerId;
        return o;
    }));

    const mePlayer = g.players.find((p) => p.id === me.playerId);
    const partner = g.players.find((p) => p.id === tradeDraft.partnerId);
    renderTradeTiles(g, $("trade-give-tiles"), me.playerId, tradeDraft.give);
    renderTradeTiles(g, $("trade-take-tiles"), partner.id, tradeDraft.take);

    $("trade-give-money").max = mePlayer.money;
    $("trade-take-money").max = partner.money;
    $("trade-give-cards").max = mePlayer.jailFreeCards;
    $("trade-take-cards").max = partner.jailFreeCards;
    $("trade-give-cards-row").classList.toggle("hidden", mePlayer.jailFreeCards === 0);
    $("trade-take-cards-row").classList.toggle("hidden", partner.jailFreeCards === 0);
}

/** Чекбоксы клеток игрока; выбор хранится в set, клетки, которых у игрока больше нет, из него убираются. */
function renderTradeTiles(g, container, ownerId, selected) {
    const tiles = g.tiles.filter((t) => g.owners[t.index] === ownerId);
    for (const i of [...selected]) {
        if (g.owners[i] !== ownerId) selected.delete(i);
    }
    if (tiles.length === 0) {
        container.textContent = "нет имущества";
        return;
    }
    container.replaceChildren(...tiles.map((t) => {
        const label = document.createElement("label");
        label.className = "trade-tile";
        const box = document.createElement("input");
        box.type = "checkbox";
        box.checked = selected.has(t.index);
        box.disabled = !tradable(g, t);
        box.onchange = () => (box.checked ? selected.add(t.index) : selected.delete(t.index));
        const swatch = document.createElement("span");
        swatch.className = "swatch";
        swatch.style.background = t.group ? `var(--${t.group.toLowerCase()})` : "var(--line)";
        const name = document.createElement("span");
        name.textContent = t.name + (g.mortgaged.includes(t.index) ? " (залог)" : "")
            + (tradable(g, t) ? "" : " — есть дома в группе");
        label.append(box, swatch, name);
        return label;
    }));
}

function openTradeEditor() {
    tradeDraft.open = true;
    $("trade-editor").classList.remove("hidden");
    renderTradeEditor(lastGame);
}

function closeTradeEditor() {
    tradeDraft.open = false;
    $("trade-editor").classList.add("hidden");
}

function resetTradeDraft() {
    tradeDraft.give.clear();
    tradeDraft.take.clear();
    for (const id of ["trade-give-money", "trade-take-money", "trade-give-cards", "trade-take-cards"]) {
        $(id).value = 0;
    }
}

$("trade-open-btn").onclick = openTradeEditor;
$("trade-close-btn").onclick = closeTradeEditor;
$("trade-partner").onchange = () => {
    tradeDraft.partnerId = $("trade-partner").value;
    tradeDraft.take.clear();
    $("trade-take-money").value = 0;
    $("trade-take-cards").value = 0;
    renderTradeEditor(lastGame);
};
$("trade-send-btn").onclick = () => {
    const num = (id) => parseInt($(id).value, 10) || 0;
    send({
        type: "PROPOSE_TRADE",
        trade: {
            toId: tradeDraft.partnerId,
            giveTiles: [...tradeDraft.give],
            takeTiles: [...tradeDraft.take],
            giveMoney: num("trade-give-money"),
            takeMoney: num("trade-take-money"),
            giveJailCards: num("trade-give-cards"),
            takeJailCards: num("trade-take-cards"),
        },
    });
    // окно закроется, когда сервер подтвердит предложение (фаза TRADE_OFFER); при ошибке оно останется открытым
};

/** Открытое предложение: видно всем, кнопки — адресату (принять/отклонить) и автору (отозвать). */
function renderTradeOffer(g, nameOf) {
    const t = g.trade;
    $("trade-offer").classList.toggle("hidden", !t);
    if (!t) return;
    if (t.fromId === me.playerId) resetTradeDraft(); // предложение принято сервером — черновик больше не нужен

    const side = (tiles, money, cards) => {
        const parts = tiles.map((i) => g.tiles[i].name + (g.mortgaged.includes(i) ? " (залог)" : ""));
        if (money) parts.push(`$${money}`);
        if (cards) parts.push(`карточки выхода из тюрьмы: ${cards}`);
        return parts.length ? parts.join(", ") : "ничего";
    };
    const from = nameOf(t.fromId);
    const to = nameOf(t.toId);
    $("trade-offer-title").textContent = `Обмен: ${from} → ${to}`;
    $("trade-offer-give").textContent = `${from} отдаёт: ${side(t.giveTiles, t.giveMoney, t.giveJailCards)}`;
    $("trade-offer-take").textContent = `${from} получает: ${side(t.takeTiles, t.takeMoney, t.takeJailCards)}`;
    const hasMortgaged = [...t.giveTiles, ...t.takeTiles].some((i) => g.mortgaged.includes(i));
    $("trade-offer-note").textContent = hasMortgaged ? "За заложенные клетки получатель сразу платит 10% от залога." : "";

    const isTo = t.toId === me.playerId;
    $("trade-accept-btn").classList.toggle("hidden", !isTo);
    $("trade-reject-btn").classList.toggle("hidden", !isTo);
    $("trade-cancel-btn").classList.toggle("hidden", t.fromId !== me.playerId);
}

$("trade-accept-btn").onclick = () => send({ type: "ACCEPT_TRADE" });
$("trade-reject-btn").onclick = () => send({ type: "REJECT_TRADE" });
$("trade-cancel-btn").onclick = () => send({ type: "CANCEL_TRADE" });

/** Панель долга: видна всем, кнопки — только должнику. */
function renderDebt(g, nameOf) {
    const d = g.debt;
    $("debt").classList.toggle("hidden", g.phase !== "PAYING_DEBT" || !d);
    if (g.phase !== "PAYING_DEBT" || !d) return;

    const debtor = g.players.find((p) => p.id === d.debtorId);
    const toWhom = d.creditorId ? `игроку ${nameOf(d.creditorId)}` : "банку";
    const mine = d.debtorId === me.playerId;
    $("debt-text").textContent = mine
        ? `Вы должны $${d.amount} ${toWhom}.`
        : `${debtor.name} должен $${d.amount} ${toWhom}.`;
    const missing = d.amount - debtor.money;
    $("debt-hint").textContent = missing > 0
        ? `Не хватает $${missing}` + (mine ? ": продайте дома или заложите имущество в панели ниже." : ".")
        : (mine ? "Денег достаточно — можно платить." : "Денег достаточно.");

    $("debt-controls").classList.toggle("hidden", !mine);
    $("pay-debt-btn").textContent = `Заплатить $${d.amount}`;
    $("pay-debt-btn").disabled = missing > 0;
}

$("pay-debt-btn").onclick = () => send({ type: "PAY_DEBT" });
$("bankrupt-btn").onclick = () => {
    if (confirm("Объявить банкротство? Всё имущество уйдёт кредитору, вы выбываете из игры.")) {
        send({ type: "DECLARE_BANKRUPTCY" });
    }
};

/**
 * Панель «Моё имущество»: все клетки игрока — застройка (для собранных групп) и залог.
 * Кнопки заранее выключаются по тем же правилам, что проверяет сервер, — сервер всё равно главный.
 */
function renderPropertyPanel(g, myTurn) {
    // во время долга должник может только собирать деньги: продавать дома и закладывать
    const inDebt = g.phase === "PAYING_DEBT" && g.debt.debtorId === me.playerId;
    const canManage = inDebt
        || (myTurn && ["WAITING_FOR_ROLL", "AWAITING_BUY_DECISION", "TURN_END"].includes(g.phase));
    const mine = g.tiles.filter((t) => g.owners[t.index] === me.playerId);

    $("property").classList.toggle("hidden", !canManage || mine.length === 0);
    if (!canManage || mine.length === 0) return;

    const money = g.players.find((p) => p.id === me.playerId).money;
    const mortgaged = new Set(g.mortgaged);
    const levelOf = (t) => g.buildings[t.index] || 0;
    const groupOf = (t) => g.tiles.filter((s) => s.type === "PROPERTY" && s.group === t.group);
    $("property-bank").textContent = `В банке: домов ${g.housesInBank}, отелей ${g.hotelsInBank}`;

    const rows = mine.map((t) => {
        const row = document.createElement("div");
        row.className = "property-row";
        row.classList.toggle("mortgaged", mortgaged.has(t.index));
        row.innerHTML = `<span class="swatch"></span><span class="property-name"></span>
            <span class="property-level"></span><span class="build-buttons"></span><span class="mortgage-slot"></span>`;
        row.querySelector(".swatch").style.background = t.group ? `var(--${t.group.toLowerCase()})` : "var(--line)";
        row.querySelector(".property-name").textContent = t.name;

        const group = t.type === "PROPERTY" ? groupOf(t) : [];
        const groupLevels = group.map(levelOf);
        const groupHasBuildings = groupLevels.some((l) => l > 0);
        const fullGroup = group.length > 0 && group.every((s) => g.owners[s.index] === me.playerId);

        // застройка — только на собранных группах
        if (fullGroup) {
            const level = levelOf(t);
            const cost = g.houseCosts[t.group];
            const noStock = level === 4 ? g.hotelsInBank === 0 : g.housesInBank === 0;
            const groupMortgaged = group.some((s) => mortgaged.has(s.index));
            row.querySelector(".property-level").replaceChildren(
                ...(level ? buildingIcons(level) : [document.createTextNode("—")]));

            const plus = document.createElement("button");
            plus.textContent = `${level === 4 ? "Отель" : "+ Дом"} $${cost}`;
            plus.disabled = level === HOTEL || level > Math.min(...groupLevels) || money < cost
                || noStock || groupMortgaged || inDebt;
            plus.title = groupMortgaged ? "Сначала выкупите заложенные улицы этого цвета" : "";
            plus.onclick = () => send({ type: "BUILD", tileIndex: t.index });

            const minus = document.createElement("button");
            minus.className = "secondary";
            minus.textContent = "−";
            minus.title = `Продать за $${cost / 2}`;
            minus.disabled = level === 0 || level < Math.max(...groupLevels);
            minus.onclick = () => send({ type: "SELL_HOUSE", tileIndex: t.index });

            row.querySelector(".build-buttons").append(plus, minus);
        }

        // залог: половина цены, выкуп — залог + 10% с округлением вверх (как на сервере)
        const value = t.price / 2;
        const btn = document.createElement("button");
        btn.className = "secondary";
        if (mortgaged.has(t.index)) {
            const cost = Math.ceil(value * 1.1);
            btn.textContent = `Выкупить $${cost}`;
            btn.disabled = money < cost || inDebt;
            btn.onclick = () => send({ type: "UNMORTGAGE", tileIndex: t.index });
        } else {
            btn.textContent = `Заложить $${value}`;
            btn.disabled = groupHasBuildings;
            btn.title = groupHasBuildings ? "Сначала продайте постройки на улицах этого цвета" : "";
            btn.onclick = () => send({ type: "MORTGAGE", tileIndex: t.index });
        }
        row.querySelector(".mortgage-slot").append(btn);
        return row;
    });
    $("property-list").replaceChildren(...rows);
}

// кнопки «+10/+50/+100» ставят сразу; поле ввода — для произвольной суммы
for (const btn of document.querySelectorAll("#auction-controls [data-step]")) {
    btn.onclick = () => send({ type: "BID", amount: currentHighestBid() + +btn.dataset.step });
}
$("bid-btn").onclick = () => send({ type: "BID", amount: parseInt($("bid-input").value, 10) });
$("pass-btn").onclick = () => send({ type: "PASS" });

function currentHighestBid() {
    return +$("bid-input").min - 1;
}

// ---------------------------------------------------------------- ошибки

let errorTimer;
function showError(text) {
    $("error").textContent = text;
    $("error").classList.remove("hidden");
    clearTimeout(errorTimer);
    errorTimer = setTimeout(() => $("error").classList.add("hidden"), 3000);
}
