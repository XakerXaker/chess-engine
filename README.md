# JavaChess — Шахматный движок на Java 17

Высокопроизводительный консольный шахматный движок, написанный на современной **Java 17** без сторонних runtime-зависимостей. Движок поддерживает протокол [Universal Chess Interface (UCI)](https://en.wikipedia.org/wiki/Universal_Chess_Interface), полный парсинг и сериализацию [нотации Форсайта — Эдвардса (FEN)](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation), битовые доски ([Bitboards](https://en.wikipedia.org/wiki/Bitboard)) и классический минимаксный поиск с отсечениями по мотивам материалов [Chess Programming Wiki](https://www.chessprogramming.org/).

## Основные архитектурные решения

### 1. Представление доски и состояния игры (`core`)
- **Гибридная схема Bitboards + Mailbox**: позиция хранится в виде 12 64-битных масок ([Bitboard](https://en.wikipedia.org/wiki/Bitboard)) для каждого типа и цвета фигур в сочетании с массивом `pieceAt[64]` для $O(1)$ определения фигуры на конкретном поле. Поля индексируются по схеме Little-Endian Rank-File Mapping (`a1 = 0`, `h8 = 63`).
- **Компактное кодирование хода (`Move`)**: каждый ход упакован в примитивный 32-битный `int` (6 бит — поле старта, 6 бит — поле назначения, 4 бита — флаги спецходов, 4 бита — ходящая фигура, 4 бита — взятая фигура). Вместе с предвыделенными списками `MoveList` на каждый полуход это полностью исключает нагрузку на сборщик мусора (Zero-Allocation) внутри дерева поиска.
- **Инкрементальное Zobrist-хэширование (`Zobrist`)**: 64-битный хэш позиции ([Zobrist hashing](https://en.wikipedia.org/wiki/Zobrist_hashing)) инициализируется детерминированным генератором `SplitMix64` и обновляется за $O(1)$ через `XOR` в методах `makeMove` / `unmakeMove` при перемещениях фигур, смене очереди хода, изменении прав на рокировку и поля взятия на проходе.

### 2. Парсинг и сериализация FEN (`fen`)
- **Полная поддержка стандарта FEN (`FenParser`)**: разбор и генерация всех шести полей [FEN-записи](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation) — расстановки фигур по горизонталям, активной стороны (`w`/`b`), битовой маски прав на рокировку (`KQkq`), целевого поля взятия на проходе (`en passant`), счетчика полуходов для правила 50 ходов и номера хода.
- **Строгая валидация (`FenParseException`)**: контроль корректности числа горизонталей и вертикалей, допустимости символов фигур, наличия ровно одного короля у каждой стороны и валидности координат взятия на проходе.

### 3. Генерация ходов и верификация Perft (`movegen`)
- **Быстрая генерация атак**: для коней, королей и пешек используются предвычисленные таблицы битовых масок, а для дальнобойных фигур (ладья, слон, ферзь) — лучевые маски с определением первого блокера через аппаратные интринсики JVM `Long.numberOfTrailingZeros` и `Long.numberOfLeadingZeros`.
- **Точность правил (`Perft`)**: генератор псевдолегальных и легальных ходов проверен тестами [Perft](https://www.chessprogramming.org/Perft) на канонических позициях ([arXiv Perft Reference](https://arxiv.org/html/2503.10822v2)), включая начальную позицию (глубина 5 — `4 865 609` узлов), позицию Kiwipete (глубина 4 — `4 085 603` узла) и эндшпильные позиции со сложными связками при взятии на проходе.

### 4. Поиск лучшего хода (`search`)
- **Principal Variation Search и Alpha-Beta**: в основе лежит алгоритм [Negamax](https://en.wikipedia.org/wiki/Negamax) с [альфа-бета отсечением](https://www.chessprogramming.org/Alpha-Beta) и поиском главной вариации (PVS) внутри цикла [итеративного углубления (Iterative Deepening)](https://en.wikipedia.org/wiki/Iterative_deepening_depth-first_search) с гибким контролем времени.
- **Таблица транспозиций (`TranspositionTable`)**: хэш-таблица ([Transposition table](https://en.wikipedia.org/wiki/Transposition_table)) на плоских примитивных массивах без объектных оберток хранит точные оценки (`EXACT`), верхние (`ALPHA`) и нижние (`BETA`) границы, глубину расчета и лучший ход, корректно нормализуя матовые оценки относительно текущей глубины (`ply`).
- **Поиск спокойствия (`Quiescence Search`)**: в листовых узлах запускается [Quiescence Search](https://www.chessprogramming.org/Quiescence_Search#Pseudo_Code) по взятиям, превращениям и уходам от шаха со `stand-pat` оценкой для устранения эффекта горизонта ([Quiescence search](https://en.wikipedia.org/wiki/Quiescence_search)).
- **Упорядочивание ходов (`Move Ordering`)**: для максимизации ранних beta-отсечений ходы сортируются на лету ([Move ordering](https://en.wikipedia.org/wiki/Move_ordering)) в следующем порядке:
  1. Лучший ход из таблицы транспозиций (TT-move).
  2. Взятия и превращения по эвристике **MVV-LVA** (*Most Valuable Victim — Least Valuable Attacker*).
  3. Два тихих хода-убийцы на каждый уровень дерева ([Killer Moves](https://rustic-chess.org/search/ordering/killers.html)).
  4. Тихие ходы по таблице исторической успешности (**History Heuristic**).
- **Селективные отсечения и продления**: реализованы продление поиска при шахе (*Check Extension*), отсечение пустым ходом (*Null Move Pruning*), сокращение глубины для поздних тихих ходов (*Late Move Reductions, LMR*), *Reverse Futility Pruning*, а также распознавание ничьих по правилу 50 ходов, повторению позиции и недостатку материала.

### 5. Статическая оценка позиции (`eval`)
- **Tapered Eval и таблицы PeSTO (`Evaluator`)**: плавная интерполяция между оценками миттельшпиля и эндшпиля ([PeSTO & Tapered Eval](https://talkchess.com/viewtopic.php?t=77728)) в зависимости от оставшегося на доске материала.
- **Позиционные факторы**: бонусы за проходные пешки, преимущество двух слонов, ладьи на открытых и полуоткрытых вертикалях, мобильность фигур, штрафы за сдвоенные и изолированные пешки, а также эндшпильная эвристика оттеснения одинокого короля соперника к краю доски (*Mop-Up Evaluation*).

## Структура проекта

- `src/main/java/com/chess/engine/core` — битовые доски (`Bitboard`), состояние позиции (`Board`), кодирование хода (`Move`, `MoveList`), Zobrist-хэширование (`Zobrist`).
- `src/main/java/com/chess/engine/fen` — парсер и сериализатор FEN-строк (`FenParser`, `FenParseException`).
- `src/main/java/com/chess/engine/movegen` — генератор ходов (`MoveGenerator`) и утилиты тестирования `Perft`.
- `src/main/java/com/chess/engine/eval` — статическая функция оценки позиции (`Evaluator`).
- `src/main/java/com/chess/engine/search` — поисковый движок (`SearchEngine`) и таблица транспозиций (`TranspositionTable`).
- `src/main/java/com/chess/engine/uci` — реализация протокола UCI и CLI-интерфейса (`UciProtocol`).
- `src/test/java/com/chess/engine` — набор тестов FEN, Zobrist, Perft и тактических позиций (`EngineTestSuite`).

## Сборка и запуск

Сборка через Maven (требуется JDK 17+):

```bash
mvn clean package
java -jar target/chess-engine-1.0.0.jar
```

Сборка и запуск тестов напрямую через стандартный компилятор JDK:

```bash
mkdir -p target/classes target/test-classes
javac -d target/classes $(find src/main/java -name "*.java")
javac -cp target/classes -d target/test-classes $(find src/test/java -name "*.java")
java -cp target/classes:target/test-classes com.chess.engine.EngineTestSuite
```

## Поддерживаемые команды UCI и CLI

- `uci` — инициализация протокола UCI.
- `isready` — проверка готовности движка (`readyok`).
- `setoption name Hash value <MB>` — изменение размера таблицы транспозиций (от 1 до 1024 МБ).
- `ucinewgame` — сброс состояния и очистка кэша перед новой партией.
- `position startpos [moves e2e4 e7e5 ...]` — установка начальной позиции и применение последовательности ходов.
- `position fen <FEN> [moves ...]` — загрузка произвольной позиции в формате FEN.
- `go depth <N>` / `go movetime <ms>` / `go wtime <ms> btime <ms> winc <ms> binc <ms>` — запуск расчета лучшего хода.
- `d` / `display` — вывод текущего состояния доски, FEN и Zobrist-ключа в консоль.
- `fen [FEN]` — вывод текущего FEN или быстрая загрузка новой FEN-строки.
- `eval` — вывод статической оценки текущей позиции.
- `perft <depth>` / `divide <depth>` — запуск теста генератора ходов на заданную глубину.
- `bench` — запуск встроенного теста скорости поиска (NPS) на наборе позиций.
- `quit` — выход из программы.
