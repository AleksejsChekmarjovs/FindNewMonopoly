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
        el.style.setProperty("--owner", owner ? colorOf[owner] : "transparent");
        el.querySelector(".tokens").replaceChildren();
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
    if (g.phase === "GAME_OVER") {
        const winner = g.players.find((p) => p.id === g.winnerId);
        $("status").textContent = `Игра окончена. Победил ${winner?.name}`;
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

// ---------------------------------------------------------------- ошибки

let errorTimer;
function showError(text) {
    $("error").textContent = text;
    $("error").classList.remove("hidden");
    clearTimeout(errorTimer);
    errorTimer = setTimeout(() => $("error").classList.add("hidden"), 3000);
}
