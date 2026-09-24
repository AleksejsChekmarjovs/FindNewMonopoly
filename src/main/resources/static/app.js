"use strict";

const PLAYER_COLORS = ["#e53935", "#1e88e5", "#43a047", "#fdd835", "#8e24aa", "#fb8c00", "#00acc1", "#6d4c41"];
const DICE_FACES = ["", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅"];

const $ = (id) => document.getElementById(id);

let socket;
let me = { playerId: null, roomId: null };
let boardBuilt = false;

// ---------------------------------------------------------------- соединение

function connect(firstMessage) {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${proto}://${location.host}/ws`);
    socket.onopen = () => send(firstMessage);
    socket.onmessage = (e) => handle(JSON.parse(e.data));
    socket.onclose = () => showError("Соединение с сервером потеряно");
}

function send(msg) {
    socket.send(JSON.stringify(msg));
}

function handle(msg) {
    switch (msg.type) {
        case "WELCOME":
            me = { playerId: msg.playerId, roomId: msg.roomId };
            break;
        case "LOBBY":
            renderLobby(msg);
            break;
        case "STATE":
            renderGame(msg.game);
            break;
        case "ERROR":
            showError(msg.message);
            break;
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
        li.textContent = p.name + (p.playerId === me.playerId ? " (вы)" : "");
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
        const tokens = document.createElement("div");
        tokens.className = "tokens";
        el.append(tokens);
        board.append(el);
    }
    boardBuilt = true;
}

function renderGame(g) {
    $("lobby-screen").classList.add("hidden");
    $("waiting-screen").classList.add("hidden");
    $("game-screen").classList.remove("hidden");
    if (!boardBuilt) buildBoard(g.tiles);

    const colorOf = {};
    g.players.forEach((p, i) => (colorOf[p.id] = PLAYER_COLORS[i]));

    // владельцы и фишки
    for (const t of g.tiles) {
        const el = $("tile-" + t.index);
        const owner = g.owners[t.index];
        el.classList.toggle("owned", !!owner);
        el.classList.toggle("mortgaged", g.mortgaged.includes(t.index));
        el.style.setProperty("--owner", owner ? colorOf[owner] : "transparent");
        el.querySelector(".tokens").replaceChildren();
        const slot = el.querySelector(".buildings");
        if (slot) slot.replaceChildren(...buildingIcons(g.buildings[t.index] || 0));
    }
    for (const p of g.players) {
        if (p.bankrupt) continue;
        const token = document.createElement("div");
        token.className = "token";
        token.style.background = colorOf[p.id];
        token.title = p.name;
        $("tile-" + p.position).querySelector(".tokens").append(token);
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
    } else {
        $("status").textContent = myTurn ? "Ваш ход" : `Ходит ${current.name}`;
    }
    $("dice").textContent = g.lastRoll ? DICE_FACES[g.lastRoll.first] + " " + DICE_FACES[g.lastRoll.second] : "";

    // последняя вытянутая карточка
    const card = $("card");
    card.classList.toggle("hidden", !g.lastCard);
    if (g.lastCard) {
        card.className = g.lastCard.deck.toLowerCase();
        card.querySelector(".card-deck").textContent =
            g.lastCard.deck === "CHANCE" ? "Шанс" : "Общественная казна";
        card.querySelector(".card-text").textContent = g.lastCard.text;
    }

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
    for (const btn of document.querySelectorAll("#actions button")) {
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
        const notes = [p.inJail ? "в тюрьме" : "", p.jailFreeCards ? `🔑×${p.jailFreeCards}` : ""].filter(Boolean).join(" ");
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

for (const btn of document.querySelectorAll("#actions button")) {
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

/**
 * Панель «Моё имущество»: все клетки игрока — застройка (для собранных групп) и залог.
 * Кнопки заранее выключаются по тем же правилам, что проверяет сервер, — сервер всё равно главный.
 */
function renderPropertyPanel(g, myTurn) {
    const canManage = myTurn && ["WAITING_FOR_ROLL", "AWAITING_BUY_DECISION", "TURN_END"].includes(g.phase);
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
                || noStock || groupMortgaged;
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
            btn.disabled = money < cost;
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
