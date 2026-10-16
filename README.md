# SkillArena v0.6.0 — Vargas Packet NPC prototype

Paper 1.20.1 / Java 17 / ProtocolLib 5.1.0.

## Setup
1. Install ProtocolLib 5.1.0 compatible with Paper 1.20.1 on the server.
2. Build via GitHub Actions or `mvn clean package`.
3. Put the resulting SkillArena jar in `plugins/` and restart.
4. `/sa boss spawn` starts the existing Vargas boss with a client-side player model.
5. `/sa boss stop` stops the fight and cleans up NPC packets.

## Design
- Hidden zombie remains authoritative for pathing, HP, collision, and skill ray hits.
- ProtocolLib sends player-info, spawn, equipment, teleport, head rotation, arm swing, destroy and remove-player-info packets.
- Each viewer is synchronized independently; join/leave and world changes are handled by the sync loop.
- Optional signed skin textures can be entered in `config.yml` under `vargas.skin`. Without these values, a default skin is used.
- Client-side interactions are intercepted for basic melee testing; attack classification and anti-cheat integration require further hardening.

## Current limitations
- This is a **prototype**. The Packet NPC has not been compiled or tested against a live Paper 1.20.1 server here.
- Client-side player movement currently uses frequent teleport packets, which may look less smooth than natural interpolation.
- Native player attack animations are limited; custom drawing/sheathe animations are deferred.
- No skin texture/signature is shipped.
- The existing boss skill and AI implementations remain prototypes.


## v0.6.1 Maven dependency fix
ProtocolLib 5.1.0 is resolved from Maven Central as `net.dmulloy2:ProtocolLib:5.1.0` with `provided` scope. Do not commit ProtocolLib.jar to this repository. Install ProtocolLib 5.1.0 separately in the server `plugins/` directory.


## v0.6.2
- Fixed Paper 1.20.1 Particle.ENCHANTMENT_TABLE constant.
- Resolved java.util.Vector vs Bukkit Vector import conflict.
- Used ProtocolLib 5.1.0 PLAYER_INFO packet constant.
- GitHub Actions Maven compilation still needs verification.

## v0.6.4
- Fixed missing `org.bukkit.util.RayTraceResult` import (five compiler errors).

### v0.6.4 build fix
- PacketVargasVisual.send: catch RuntimeException instead of checked InvocationTargetException, which the current ProtocolLib API does not declare.

## v0.7.0
- Vargas boss bar displays current/max HP numerically; damage is tracked as virtual HP, avoiding vanilla attribute limits.
- More dramatic skill trails, circles, impact bursts, ultimate particles and sound.
- Visual effects do not alter skill damage or cooldowns.

## v0.7.1 - Player HP display
- Player max HP is 100 (actual Bukkit health).
- Action bar shows HP current/max, MP, ultimate charge and skill cooldowns.
- Vanilla hearts are scaled to ten hearts for a compact visual gauge; numeric HP is authoritative.
- Health scale is set on login and plugin enable.

## v0.7.2 管理者ウルトチャージ
- `/sa ultcharge` : 自分のウルトを100%にする（OP権限）
- `/sa ultcharge <プレイヤー>` : 対象を100%にする（コンソール可）
- `/sa ultcharge <プレイヤー> <0-100>` : 対象のゲージを指定値に設定
- `/sa gauge` : 従来のコマンド互換（自分を100%）
- 権限: `skillarena.admin`


## v0.7.3 Visual combat
- Family-0 elemental projectiles move tick-by-tick with collision and delayed damage.
- Fireball: dense flame core, lava embers and sparks. Other schools have distinct trails.
- Martial attacks and sword skills use curved slash ribbons.
- Damage, MP and cooldown values are unchanged.

## v0.7.4
- 衝撃拳、ブラスト、テンペスト、アースクエイク、ヴァルガスの衝撃波にウィンドチャージ風の水平＋垂直ノックバックを追加。
- 衝撃波の着弾地点に風圧リングと爆風のパーティクル・効果音を追加。
- 強化版の吹き飛ばしを強め、最大速度を制限。

## v0.8.0 - Stamina
- Food bar is now stamina (20 = 100%). Natural hunger changes are disabled.
- Sprinting drains 0.22 stamina per 5 ticks; normal recovery is 0.16 per 5 ticks, sneaking 0.28.
- While stamina remains, walk speed is 0.32 (vanilla 0.2) and Jump Boost I is applied.
- At zero stamina, speed returns to 0.2 and sprinting is stopped.
- HUD shows ST percent; stamina resets on join and is not persisted.


## v0.8.1 スタミナ切れの行動不能
- STが0になった瞬間、移動・ジャンプ不可。視点操作は可能。
- スタミナが回復途中でも移動不可を維持し、100%に戻った時だけ解除。
- スタミナ切れ中は移動速度0、水平・垂直移動をイベントで阻止。
- 回復速度は従来通り（停止状態で5tickごとに0.16/20回復）。
- HUDに「ST 回復中」を表示し、枯渇・全回復時にメッセージを表示。
- 管理者のテレポート等の位置変更イベントは移動イベントと別扱い。


## v0.8.2 — Stamina exhaustion
- When stamina reaches 0, exhaustion lasts until the meter returns to 20/20.
- Recovery during exhaustion: +0.50 food units every 5 ticks (40 updates, approximately 10 seconds).
- Exhausted players cannot move, jump, use skills, sword skills, ultimates, or Break Step/dodge.
- Existing dodge i-frames, counter stance and speed buff are removed on exhaustion; no defensive invulnerability applies during exhaustion.
- Camera rotation and skill GUI access remain available.


## v0.8.3 — OVERHEAT UI
- Replace the Japanese exhaustion label with red bold `OVERHEAT` in the action bar, retaining the recovery percentage.
- On reaching zero stamina, show `OVERHEAT / SYSTEM LOCKED` title, warning sound and chat message.
- At full recovery (approximately 10 seconds), show `SYSTEM READY` title, confirmation sound and chat message.
- No changes to stamina recovery rate or defensive restrictions.


## v0.8.4
- Fixed an extra closing brace in the shockwave ultimate (SkillArenaPlugin.java), which caused 51 cascading Java syntax errors in v0.8.3.

## v0.8.5
- Food level 6 is treated as stamina 0%; the usable stamina range is food 6-20.
- OVERHEAT begins at food level 6, with a fixed approximately 10-second recovery to food level 20.
- Sprint drain and idle/sneak recovery are proportionally adjusted for the new 14-point usable range.
- All player skill, sword technique, and ultimate damage applied via `hit` is increased by 50%; boss damage unchanged.


## v0.8.6 — ヴァルガス外見・アクセサリ
- 剣帝ヴァルガスはネザライト防具・剣を装備した通常のゾンビモデルを使用（Packet fake-player表示を使わない）。
- ネザライト防具の防御値をボスHP計算に反映しない（防具は外見のみ）。
- `/sa accessories` またはスキル構築GUIのアクセサリボタンから専用4スロット装備GUIを開く。
- アクセサリ6種。所持済みアクセサリをクリックで空きスロットに装備、装備枠クリックで解除。所持・装備状態はプレイヤーYAMLへ永続化。
- 効果はヴァルガス戦の参加者か、将来のダンジョンAPIで指定されたプレイヤーにのみ適用。通常PvPでは無効。
- ボス討伐時、参加者へ未所持アクセサリを1種ランダム配布。テスト用 `/sa accessories give <0-5>` (OP)。

## v0.8.7
- ウルト発動前1.5秒は位置移動不能（視点変更可）。発動演出の後に技を実行。
- フォースマジックは4属性、セブンスマジックは7属性の球体を周囲に生成し、敵へ飛翔・接触時にダメージ。遮蔽物で消滅。

## v0.8.8
アースクエイク／グランドクロス：地面から高さ約1ブロックの白い雲と茶色い土片の混合衝撃波が、リング状に外へ広がる演出に変更。衝撃波が接触した敵にダメージとノックバックを与える。


## v0.8.9 ボス機動AI
- ヴァルガスは速度ベクトルによって追跡・横移動・距離調整します。
- 遠距離でジャンプ突進し、ジェノサイドは跳躍ダッシュします。
- プレイヤーの飛翔スキルが接近したとき、確率・クールダウン付きで左右に回避します。
- 攻撃予告中は移動を抑制し、攻撃の視認性を維持します。


## v0.9.0: 標準機動アクション
- スキルを装備せずに空中でジャンプキーをもう一度押すと二段ジャンプ（ST 2/14、着地で再使用可能）。
- スニークキーを0.09～0.35秒間隔で二度押すと短距離高速ダッシュ（ST 1.5/14、CT 1.8秒）。
- OVERHEAT中・ウルト詠唱中・行動ロック中は両方使用不可。
- ブレイクステップ（スニーク＋左クリック）とは独立したアクション。
- サバイバル・アドベンチャーモード限定。


## v0.9.1 — 剣帝ヴァルガス分身
フェーズ2移行時にHP400のネザライト装備ゾンビ分身を1体召喚。追跡・横移動・跳躍突進・簡易飛翔攻撃回避・BREAK/BLAST/TEMPEST/GENOCIDE系の弱化攻撃を行う。分身はボス本体とHPを共有せず、倒しても追加報酬なし。ボス戦終了時は自動消去。


## v0.9.2 進化スキル修正
- 「・連」（tier 2）は3回の時差攻撃（4tick間隔）。合計威力を3分割し、各段でダメージ・パーティクル・斬撃音を処理。飛翔弾は着弾後に連撃。
- 連撃1発の使用で熟練度・コンボは1回だけ加算。
- 熟練度の進捗を技能系統ごとに独立管理・永続化（従来は全系統共通カウンタ）。
- 転位の移動だけで攻撃熟練度が増えないよう修正。
- ビルドと実機での動作検証は別途必要。

## v0.9.3 Mobility speed tuning
- With stamina, normal walk speed is now 0.37 (previously 0.32).
- Sprint walk-speed setting is 0.44 (previously 0.32), in addition to Minecraft sprint multiplier.
- OVERHEAT movement lock and 10-second recovery remain unchanged.
- Speed accessory bonuses still apply in boss and dungeon encounters.


## v0.9.4
- プレイヤーの落下ダメージ無効。
- `/sa heal [個数]` (OP) で専用回復薬を配布。右クリックでHP40回復、12秒CT、ボス戦参加者またはワールド名に `dungeon` を含むダンジョン内限定。
- 剣帝ヴァルガスと分身の戦闘移動AIを改善。追跡、回り込み、距離調整、予告中の微移動を追加。

## v0.9.5 コマンド操作改善
- `/boss spawn`, `/boss stop` でボス操作を短縮（OP）。
- `/ultcharge`, `/ultcharge 50`, `/ultcharge プレイヤー 75` を追加（OP）。
- `/sa ultcharge 50` も自身のゲージに対応。
- `/sa`、`/boss`、`/ultcharge` のTab補完（権限に応じて表示）。


## v0.9.6 ボス移動不具合修正
- 原因: ヴァルガス本体と分身のゾンビを `setAI(false)` で生成しており、通常の追跡・経路探索が無効になっていた。
- 本体・分身のAIを有効化し、参加プレイヤーを `setTarget` で追跡するよう変更。既存の横移動・跳躍・回避・技AIも維持。
- 本体・分身の自然攻撃による想定外のダメージを防止し、日光炎上も無効化。
- 実サーバー上での経路探索・戦闘挙動は未検証。


### v0.9.7
- 剣帝ヴァルガスが12～45ブロック離れたプレイヤーへ大ジャンプし、着地時に半径6～7.5ブロックの衝撃波を発生。
- 空中追尾補正、予告、着地エフェクト、ノックバック、クールダウンを追加。


## v0.9.8
- フェーズ移行のタイトル通知を廃止し、ボス周囲の火花（CRIT/FLAME/SOUL_FIRE_FLAME）で視覚的に演出。
- 大跳躍・震撃のチャット警告を廃止。
- 分身召喚のチャット通知を廃止し、既存の召喚パーティクルを維持。

## v0.9.9
- プレイヤー死亡時にインベントリ内のアイテムをドロップせず保持します。経験値・レベルも保持します。
- ボスや分身の既存ドロップ制御は変更しません。


## v1.0.0 近接攻撃強化
- 武術系の通常・進化スキル（近距離打撃、範囲衝撃波）の基本ダメージを従来比2.5倍に変更。連撃は合計ダメージを3発に分配。
- 剣技ULT中の4種類の近接剣技のダメージを従来比1.8倍に変更。
- 遠距離スキル、他属性スキル、ボス攻撃力、MP・CTは据え置き。


## v1.0.1
- オーバードライブ/アンリミテッドドライブ中、被弾によるノックバックを無効化（移動操作・ダッシュは維持）。効果時間10秒/12秒。
- 攻撃ULTの威力を約65%増加（衝撃波、魔法球、ジャッジメント、居合、剣技）。既存の全スキル威力は維持。

## v1.0.2
- ジャッジメント／エスカトンジャッジメント：地面の光輪、電撃火花、天から降る光柱、落雷エフェクト、着弾時の閃光を追加。
- 進化版は着弾範囲と光柱が大きく、最後の5撃は特に派手な演出。落雷は演出のみで炎上や追加ダメージなし。
- ダメージ倍率・着弾間隔はv1.0.1から維持。

## v1.0.3
- 居合切りと覇王一閃の一撃威力を上方修正。居合切りのULT内部基礎値35→75、覇王一閃35→100（既存のULT倍率1.65および共通ダメージ倍率は従来どおり適用）。射程、判定幅、準備時間、CTは据え置き。

## v1.0.4 — 「・極」威力調整
- 16系統の「・極」スキルの基礎ダメージを従来比 **1.25倍（+25%）** に調整。
- 「・連」の3連撃、「・改」、通常スキル、ULTの威力は変更なし。
- 転位・極は移動専用のためダメージ増加対象外。


## v1.0.5 熟練進化（弐・参・肆・極）
- 各系統の第4段階スキル（旧「・極」枠）は熟練度7で「・弐」として解放。
- 熟練度8で「・参」、9で「・肆」、10で「・極」に自動進化。
- 第4段階の基礎威力倍率: 弐 x1.10 / 参 x1.15 / 肆 x1.20 / 極 x1.25（従来の極と同値）。
- 構築コスト・MP消費・クールタイム・スキル枠数は据え置き。
- 進化は名前と威力に反映され、再装備不要。移動専用「転位」は名前のみ進化。
- 既存セーブの熟練度はそのまま引き継ぎ、熟練度10なら従来通り「・極」。


## v1.0.6 転位の熟練進化
- 転位・弐: 最大11ブロック、1回分のチャージ。
- 転位・参: 最大14ブロック、2回分のチャージ。
- 転位・肆: 最大17ブロック、3回分のチャージ。
- 転位・極: 最大20ブロック、4回分のチャージ。
- チャージはそれぞれ従来のCTで個別に回復。使用ごとのMP・構築コストは不変。
- 転位成功時に熟練度が増える。通常・改・連は従来距離のまま。

## v1.0.7 転位・背
- 転位の第3段階「転位・連」を「転位・背」に変更。
- 視線の先16ブロック以内にいる対象の背後（向いている方向の反対側）に瞬間移動。
- 背後に安全な足場がない、または対象がいない場合は不発となりMP・チャージを返却。
- MP消費、クールタイム、構築コストは据え置き。


## v1.0.8 ボスの攻撃間隔と戦闘機動
- ヴァルガスの全攻撃後に50〜80tick（2.5〜4秒）の攻撃不可時間を設定。予備動作時間は別途加算。
- BLAST/TEMPEST/RAGNAROKは共有の範囲攻撃CT（予備動作後135〜180tick）を持ち、連続範囲攻撃を防止。
- 大跳躍・震撃は15ブロック以上でのみ発動し、着地後にも攻撃休止。CTは260tick。
- 戦闘専用のフェイント・側面移動・接近ジャンプ（追加ダメージなし）を追加。
- フェーズ2の分身も攻撃間隔を100〜164tickに延長。

## v1.0.9 ジャンプ前方移動強化
- プレイヤーの二段ジャンプは視線の水平方向へより強く跳ぶ（前方速度0.35→0.95、上方向0.77→0.82）。ST消費2、着地ごと1回の仕様は維持。
- ヴァルガスの接近ジャンプ・追跡ジャンプも前方への跳躍距離を強化。攻撃威力やCTは据え置き。


## v1.1.0: 上位ULTの管理者解放
`/ultunlock` 自分の上位ULT全8種解放、`/ultunlock 3` 自分の覇王一閃を解放、`/ultunlock プレイヤー名 all` 他プレイヤーの全種解放、`/ultunlock プレイヤー名 3` 個別解放。`/sa ultunlock` も対応。管理者権限 `skillarena.admin` が必要。解放状態は既存の `ultUses` を20以上にする方式で永続保存され、`/skills` のULTメニューから選択できる。

ULT ID: 0 グランドクロス、1 セブンスマジック、2 エスカトンジャッジメント、3 覇王一閃、4 オブセッション、5 クロノブレイク、6 アンリミテッドドライブ、7 ラグナロク。

## v1.1.1: ボス戦の時間制限撤廃
- 剣帝ヴァルガス戦の10分（12,000tick）強制終了を削除。時間経過による敗北なし。
- 全参加者が離脱・死亡・範囲外になった場合の戦闘終了判定は維持。
- DPSチェックは今回追加せず、既存のフェーズ・HP・行動パターンを維持。

## v1.1.2: パーティ機能
- `/party create` で作成（最大4人）、`/party invite <name>` 招待（60秒）、`/party accept` 参加。
- `/party list`, `/party leave`, `/party kick <name>`, `/party disband`。`/pt` と `/sa party` も使用可能。Tab補完対応。
- パーティ参加中はサイドバーにメンバーの現在HP/MPを約0.5秒ごとに表示。既存の自分のアクションバーは維持。
- ボス戦 `/boss spawn` は、従来の周囲24ブロック自動参加ではなく、同一ワールドにいるオンラインパーティメンバーを参加者として使用。ソロ時は本人のみ。
- パーティはサーバー稼働中の一時データで、リーダーのログアウト時に解散。ダンジョン専用インスタンス/マッチングは未実装。

## v1.1.3 — PvE party friendly fire
- In Vargas boss fights, participants in the same party cannot damage one another.
- In worlds with `dungeon` in the world name, party members cannot damage one another.
- Applies to skill targeting, area attacks, direct damage, normal melee and player-fired projectiles.
- Outside these PvE contexts, PvP behavior is unchanged. Players not in the same party are not protected.


## v1.1.3 PvE friendly fire
Boss participants cannot damage one another. Dungeon party members cannot damage one another. Direct hits, projectiles, and SkillArena skill/ULT targeting exclude allies. PvP outside these PvE contexts is unchanged.


## v1.1.4: レイド参加者のフレンドリーファイア防止
- レイド戦の味方判定は通常パーティではなく、レイド参加者集合を参照します。異なるパーティに所属していても同じレイドなら攻撃が通りません。
- 近接・射撃・通常スキル・ULT・範囲スキルのプレイヤー間ダメージとターゲット選択に適用します。
- 剣帝ヴァルガスはレイド登録対象です。開始時に参加者を登録し、終了時に登録解除します。
- 今後追加するレイドボスは `RaidManager.startRaid(encounterId, participants)` / `endRaid(encounterId)` を呼び出すことで同じ保護を利用できます。
- レイド外の通常PvPと従来のダンジョン内パーティ保護は変更しません。

## v1.1.5: Party training bots
- `/testbot spawn` creates a simulated combat companion (max 3 per admin); `/testbot clear`, `/testbot list`, `/testbot skills`.
- `/testbot set <slot 1-4> <skill ID 0-7>` sets each owned bot's four skill slots. Skill IDs: 0 火炎弾, 1 氷槍, 2 雷撃, 3 光弾, 4 斬撃, 5 衝撃拳, 6 空間刃, 7 生命吸収.
- Bots are AI-controlled zombie entities (not actual Player connections). They simulate skills, MP consumption/regeneration, cooldowns, dodges, rushes, and potion healing below 40 HP (3 charges; 12s cooldown).
- They can damage Vargas's virtual HP and echoes if their owner participates in the raid; their damage is not treated as player damage for rewards. They target hostile monsters only, never players or other training bots.
- Training bots are temporary and despawn on server shutdown or owner logout. They are not counted in the party's four-player membership or its scoreboard, and do not receive party loot.


## v1.1.6: Raid boss damage fix
- Scripted Vargas and Echo attacks explicitly deal damage to raid players even when vanilla zombie melee is disabled.
- Boss scripted hits reset vanilla no-damage ticks; SkillArena's active dodge i-frames still apply.
- Companion test bots now take damage through their virtual 100 HP pool from Vargas and Echo skills, including area attacks and Skyfall.
- Bot HP decreases are reflected in `/testbot list`; bots disappear on zero HP.
- Existing raid friendly-fire protection for players is unchanged.
