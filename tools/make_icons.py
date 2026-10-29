#!/usr/bin/env python3
"""Generate app/src/main/assets/icons/catalog.json (the built-in icon library) from Material Design Icons.

Usage:
    python3 tools/make_icons.py [--mdi DIR] [--check]

--mdi DIR   an unpacked @mdi/svg package (the folder that contains meta.json and svg/). If omitted the script runs
            `npm pack @mdi/svg` in a temporary directory and unpacks it (needs network access to the npm registry).
--check     only validate the existing catalog.json (JSON, must-haves, duplicate ids, path data, size) and exit.

Source: https://github.com/Templarian/MaterialDesign (Apache-2.0). The licence text is copied to
app/src/main/assets/icons/LICENSE-MDI.txt. Icons are 24x24 single-path glyphs, drawn white by the app and
tinted on demand.

Catalog format (compact):
  {"v":1,"source":"...","categories":["Controls",...],
   "icons":[{"i":"heart","c":3,"t":"love like life health","p":"M12 21.35L10.55 20.03..."}, ...]}
  i = id (the MDI name, or a hand-made extra), c = index in categories, t = extra search tags (space separated,
  the tokens of the id itself are always searched too), p = SVG path data (24x24 viewBox).
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "icons")
OUT = os.path.join(OUT_DIR, "catalog.json")
MAX_BYTES = 1200 * 1024

CATEGORIES = [
    "Controls", "Game", "Arrows", "Audio", "Shapes", "Status", "Shop & Money", "System",
    "People", "Nature", "Animals", "Weather", "Objects", "Food", "Sport", "Emoji", "Travel", "Time", "Math",
    # game-icons set
    "Weapons", "Armor", "Treasure", "Magic", "Monsters", "Heroes", "Items", "Vehicles", "Elements", "Board & Cards", "Buildings", "Wildlife",
]
SETS = ["ui", "game"]
CI = {n: i for i, n in enumerate(CATEGORIES)}

# --------------------------------------------------------------------------------------------- explicit lists
EXPLICIT = {
    "Controls": """play pause stop play-circle play-circle-outline pause-circle pause-circle-outline stop-circle stop-circle-outline
        play-box play-box-outline skip-next skip-previous skip-forward skip-backward fast-forward rewind step-forward step-backward
        repeat repeat-once repeat-off shuffle shuffle-variant refresh restart reload sync undo redo undo-variant redo-variant
        menu menu-open dots-vertical dots-horizontal close close-thick close-circle close-circle-outline close-box close-box-outline
        check check-bold check-circle check-circle-outline check-all plus plus-thick plus-circle plus-circle-outline plus-box
        minus minus-thick minus-circle minus-circle-outline minus-box home home-outline home-variant exit-run exit-to-app logout login
        door-open door-closed fullscreen fullscreen-exit arrow-expand arrow-collapse arrow-expand-all arrow-collapse-all
        magnify magnify-plus magnify-minus eye eye-off eye-outline delete delete-outline pencil pencil-outline content-save
        content-save-outline download upload share-variant share link link-variant camera image image-outline
        lock lock-open lock-open-variant lock-outline lock-open-outline key key-variant key-outline
        information information-outline information-variant help-circle help-circle-outline help
        bell bell-outline bell-off bell-ring cursor-default cursor-pointer gesture-tap gesture-swipe gesture-swipe-up
        gesture-swipe-down gesture-swipe-left gesture-swipe-right gesture-pinch gesture-spread gesture-double-tap
        hand-pointing-up hand-pointing-right hand-pointing-left hand-pointing-down thumb-up thumb-down thumb-up-outline thumb-down-outline
        bookmark bookmark-outline pin pin-outline tune filter filter-outline sort sort-ascending sort-descending
        view-grid view-list view-dashboard toggle-switch toggle-switch-off toggle-switch-outline checkbox-marked checkbox-blank-outline
        checkbox-marked-outline checkbox-blank radiobox-marked radiobox-blank drag drag-vertical drag-horizontal resize
        rotate-left rotate-right rotate-3d flip-horizontal flip-vertical crosshairs crosshairs-gps target bullseye bullseye-arrow
        vibrate brightness-6 brightness-5 qrcode power power-standby swap-horizontal swap-vertical history camera-flip hand-back-left
        cursor-move gesture touch-app fingerprint account-circle chat chat-outline message message-outline email email-outline comment
        comment-outline send send-outline cart cart-outline cart-plus cart-minus""",
    "Game": """sword sword-cross shield shield-outline shield-half-full shield-plus shield-sword shield-crown shield-cross shield-star shield-check
        gamepad gamepad-variant gamepad-variant-outline gamepad-square gamepad-round controller controller-classic bow-arrow axe axe-battle
        magic-staff wizard-hat treasure-chest treasure-chest-outline sack bottle-tonic bottle-tonic-plus bottle-tonic-skull flask flask-outline
        dice-1 dice-2 dice-3 dice-4 dice-5 dice-6 dice-d20 dice-d12 dice-d10 dice-d8 dice-d6 dice-d4 dice-multiple cards cards-playing
        cards-spade cards-heart cards-diamond cards-club chess-king chess-queen chess-rook chess-bishop chess-knight chess-pawn puzzle
        puzzle-outline ladder skull skull-outline skull-crossbones ghost ghost-outline crown crown-outline crown-circle bomb pistol tank
        medal medal-outline trophy trophy-outline trophy-award trophy-variant trophy-variant-outline podium podium-gold podium-silver podium-bronze
        heart heart-outline heart-half heart-half-full heart-broken heart-broken-outline heart-plus heart-multiple pac-man space-invaders
        pokeball one-up boomerang mace spear knife-military hammer-wrench hammer anvil sword-cross-variant castle fire fire-circle flash flash-circle
        lightning-bolt lightning-bolt-outline lightning-bolt-circle star-four-points star-four-points-outline star-face star-shooting rocket
        rocket-launch rocket-outline ufo ufo-outline robot robot-outline robot-angry robot-happy bug bug-outline flag flag-outline flag-checkered
        flag-triangle flag-variant map map-outline map-marker map-marker-outline compass compass-outline lighthouse tent bonfire campfire
        fireplace candle ninja alien alien-outline android-head dna atom magnet magnet-on lightbulb lightbulb-on lightbulb-outline lightbulb-on-outline
        diamond diamond-outline diamond-stone key-star key-chain hourglass hourglass-empty hourglass-full hourglass-end timer-sand timer-sand-empty
        sigma emoticon-devil emoticon-dead skull-scan radioactive biohazard nuke bullet shoe-sneaker run run-fast walk jump-rope arm-flex arm-flex-outline
        hand-heart hand-coin hand-coin-outline cannon target-variant tooltip-star bow-tie crystal-ball tournament triforce""",
    "Arrows": """arrow-left arrow-right arrow-up arrow-down arrow-left-bold arrow-right-bold arrow-up-bold arrow-down-bold
        arrow-left-thick arrow-right-thick arrow-up-thick arrow-down-thick arrow-top-left arrow-top-right arrow-bottom-left arrow-bottom-right
        arrow-left-circle arrow-right-circle arrow-up-circle arrow-down-circle arrow-left-circle-outline arrow-right-circle-outline
        arrow-up-circle-outline arrow-down-circle-outline arrow-left-box arrow-right-box arrow-up-box arrow-down-box
        arrow-left-bold-circle arrow-right-bold-circle arrow-up-bold-circle arrow-down-bold-circle
        arrow-left-bold-circle-outline arrow-right-bold-circle-outline arrow-up-bold-circle-outline arrow-down-bold-circle-outline
        arrow-left-drop-circle arrow-right-drop-circle arrow-up-drop-circle arrow-down-drop-circle
        chevron-left chevron-right chevron-up chevron-down chevron-double-left chevron-double-right chevron-double-up chevron-double-down
        chevron-left-circle chevron-right-circle chevron-up-circle chevron-down-circle chevron-triple-left chevron-triple-right
        chevron-triple-up chevron-triple-down menu-left menu-right menu-up menu-down triangle-small-up triangle-small-down
        arrow-left-right arrow-up-down arrow-left-right-bold arrow-up-down-bold arrow-collapse-left arrow-collapse-right arrow-collapse-up
        arrow-collapse-down arrow-expand-left arrow-expand-right arrow-expand-up arrow-expand-down arrow-split-horizontal arrow-split-vertical
        arrow-u-left-top arrow-u-right-top arrow-u-up-left arrow-u-up-right arrow-u-down-left arrow-u-down-right arrow-u-left-bottom
        arrow-u-right-bottom arrow-left-top arrow-right-top arrow-left-bottom arrow-right-bottom arrow-left-top-bold arrow-right-top-bold
        arrow-left-bottom-bold arrow-right-bottom-bold arrow-projectile arrow-decision arrow-decision-outline arrow-down-left arrow-down-right
        arrow-up-left arrow-up-right arrow-down-left-bold arrow-down-right-bold arrow-up-left-bold arrow-up-right-bold
        subdirectory-arrow-left subdirectory-arrow-right redo-variant undo-variant transfer-left transfer-right transfer-up transfer-down
        reply forward share-all-outline axis-arrow axis-x-arrow axis-y-arrow rotate-orbit rotate-360 sync-circle cached autorenew
        directions fast-forward-outline rewind-outline page-first page-last unfold-more-horizontal unfold-less-horizontal
        unfold-more-vertical unfold-less-vertical triangle triangle-outline triangle-down triangle-down-outline""",
    "Audio": """volume-high volume-medium volume-low volume-off volume-mute volume-minus volume-plus volume-variant-off volume-vibrate volume-source
        music music-off music-note music-note-off music-note-plus music-note-eighth music-note-half music-note-quarter music-note-sixteenth
        music-note-whole music-circle music-circle-outline music-box music-clef-treble music-clef-bass playlist-music playlist-play
        playlist-plus playlist-check microphone microphone-off microphone-outline microphone-variant headphones headphones-off
        headphones-settings headset headset-off speaker speaker-off speaker-wireless speaker-multiple equalizer equalizer-outline
        waveform sine-wave square-wave sawtooth-wave triangle-wave bell-ring-outline guitar-acoustic guitar-electric guitar-pick piano
        violin saxophone trumpet metronome album disc cassette radio amplifier earbuds bugle ocarina tune-vertical sound-wave-off
        surround-sound bullhorn bullhorn-outline cosine-wave knob record record-circle record-circle-outline""",
    "Shapes": """circle circle-outline circle-double circle-half circle-half-full circle-opacity circle-small square square-outline square-rounded
        square-rounded-outline rectangle rectangle-outline rhombus rhombus-outline triangle triangle-outline pentagon pentagon-outline hexagon
        hexagon-outline octagon octagon-outline decagram decagram-outline hexagram hexagram-outline octagram octagram-outline star star-outline
        star-half star-half-full star-circle star-circle-outline star-four-points star-four-points-outline star-three-points ellipse
        ellipse-outline cube cube-outline sphere cone cylinder pyramid shape shape-outline shape-polygon-plus heart heart-outline
        vector-triangle vector-square vector-circle vector-polygon vector-line vector-curve vector-rectangle blur-linear blur radiobox-marked
        checkerboard grid grid-large dots-grid dots-hexagon dots-square dots-triangle dots-circle format-list-bulleted
        card card-outline cards-variant stop-circle-outline minus-box-outline plus-box-outline asterisk plus-minus-variant
        crop-square crop-portrait crop-landscape crop-free diameter ruler ruler-square border-all border-none""",
    "Status": """check check-bold check-circle check-circle-outline check-decagram check-decagram-outline check-all check-network
        close close-thick close-circle close-circle-outline close-octagon close-octagon-outline close-network
        alert alert-outline alert-circle alert-circle-outline alert-octagon alert-octagon-outline alert-decagram alert-box alert-rhombus
        information information-outline information-variant help help-circle help-circle-outline help-rhombus progress-check progress-clock
        progress-alert progress-close progress-download progress-upload progress-wrench loading timer-sand shield-check shield-alert shield-off
        shield-lock shield-key bell-alert bell-badge bell-plus bell-check bell-remove bell-sleep new-box new-releases new-box
        thumb-up thumb-down star-circle heart-circle emoticon-happy emoticon-sad emoticon-neutral emoticon-cool emoticon-wink
        flag-variant-outline cloud-check cloud-alert cloud-off-outline wifi wifi-off wifi-strength-1 wifi-strength-2 wifi-strength-3 wifi-strength-4
        signal signal-off signal-cellular-1 signal-cellular-2 signal-cellular-3 battery battery-outline battery-charging battery-alert
        battery-10 battery-20 battery-30 battery-40 battery-50 battery-60 battery-70 battery-80 battery-90 battery-low battery-high battery-medium
        bluetooth bluetooth-off bluetooth-connect bug-check bug-outline lock lock-open sync-alert sync-off update upgrade
        traffic-light traffic-cone octagon-outline alarm-light alarm-light-outline police-badge exclamation""",
    "Shop & Money": """cart cart-outline cart-plus cart-minus cart-check cart-heart basket basket-outline basket-plus shopping shopping-outline
        store store-outline store-plus storefront storefront-outline gift gift-outline gift-open gift-open-outline wallet wallet-outline
        wallet-plus wallet-giftcard credit-card credit-card-outline credit-card-plus cash cash-multiple cash-100 cash-plus cash-minus cash-check
        cash-register currency-usd currency-eur currency-gbp currency-jpy currency-cny currency-inr currency-krw currency-rub currency-brl
        currency-try currency-btc currency-eth currency-sign piggy-bank piggy-bank-outline bank bank-outline sale sale-outline tag tag-outline
        tag-heart tag-multiple tag-plus tag-text tag-heart-outline ticket ticket-outline ticket-percent ticket-confirmation gold treasure-chest
        sack sack-percent diamond diamond-stone diamond-outline crown crown-outline hand-coin hand-coin-outline percent percent-outline
        bitcoin ethereum wallet-travel package-variant package-variant-closed package-variant-closed-plus archive cash-lock shopping-search
        cart-arrow-down cart-arrow-up cart-arrow-right cart-remove cart-off barcode barcode-scan qrcode-scan receipt receipt-text coin-outline
        safe safe-square hand-extended handshake finance chart-line chart-bar chart-pie trending-up trending-down""",
    "System": """cog cog-outline cogs cog-box cog-refresh cog-sync cog-transfer settings-helper tune tune-variant tune-vertical wrench wrench-outline
        wrench-cog screwdriver hammer hammer-wrench tools toolbox toolbox-outline power power-plug power-plug-off power-standby
        monitor monitor-dashboard laptop cellphone cellphone-off tablet keyboard keyboard-outline mouse remote console-line console memory
        chip cpu-64-bit database database-outline server harddisk usb sd sim folder folder-outline folder-open folder-open-outline folder-plus
        folder-zip file file-outline file-plus file-document file-document-outline file-image file-music file-video content-copy content-cut
        content-paste content-save content-save-all clipboard clipboard-outline clipboard-text clipboard-check clipboard-list text text-box
        format-text format-size format-bold format-italic format-underline format-align-left format-align-center format-align-right
        format-align-justify format-list-numbered code-tags code-braces code-brackets bash console-network application application-outline
        application-cog window-maximize window-minimize window-restore window-close window-open window-closed theme-light-dark
        invert-colors palette palette-outline palette-swatch format-color-fill format-paint eyedropper brush brush-variant pencil-ruler
        draw spray spray-bottle eraser eraser-variant selection select select-all select-off cursor-text translate web earth earth-box
        earth-off web-clock layers layers-outline layers-triple layers-plus layers-minus image-multiple image-edit image-plus image-filter-frames
        movie movie-outline video video-outline video-off camera camera-outline camera-off camera-plus camera-flip-outline camera-switch
        history timeline timeline-outline source-branch source-commit source-fork source-merge source-pull github qrcode qrcode-scan
        language-java language-javascript language-python language-html5 language-css3 android android-studio apple google-play bluetooth-transfer
        nfc usb-flash-drive printer printer-outline scanner trash-can trash-can-outline delete-forever delete-sweep recycle
        wifi cloud cloud-outline cloud-upload cloud-download cloud-sync cloud-search cloud-lock cloud-print""",
    "People": """account account-outline account-circle account-circle-outline account-plus account-plus-outline account-minus account-check
        account-remove account-multiple account-multiple-outline account-multiple-plus account-group account-group-outline account-star
        account-heart account-key account-lock account-alert account-box account-box-outline account-search account-supervisor
        account-tie account-cowboy-hat account-hard-hat account-child account-child-circle account-injury account-tag account-switch
        account-convert account-cog account-eye account-voice account-details human human-male human-female human-child human-greeting
        human-handsup human-handsdown human-male-child human-male-female human-pregnant human-wheelchair human-queue human-scooter
        baby baby-face baby-face-outline face-man face-man-outline face-woman face-woman-outline face-man-shimmer face-woman-shimmer
        face-agent face-profile face-recognition mother-heart mother-nurse family-tree hand hand-back-right hand-front-left hand-front-right
        hand-peace hand-okay hand-clap hand-wave hand-saw hand-water hand-heart hand-extended hand-pointing-up hand-pointing-down
        hand-pointing-left hand-pointing-right hand-pointing-up hand-left hand-right fist hand-cycle emoticon emoticon-happy emoticon-sad
        emoticon-neutral emoticon-cool emoticon-wink emoticon-angry emoticon-cry emoticon-excited emoticon-kiss emoticon-sick
        emoticon-tongue emoticon-poop emoticon-lol emoticon-confused emoticon-dead emoticon-devil emoticon-frown shield-account
        ninja karate dance-ballroom yoga meditation walk run run-fast swim horse-human horse-variant wizard-hat sword-cross
        police-badge police-badge-outline doctor stethoscope hospital hospital-box-outline medical-bag pill needle bandage ear-hearing
        eye eye-outline brain tooth tooth-outline bone heart-pulse hospital-building virus virus-outline""",
    "Nature": """tree tree-outline pine-tree pine-tree-variant palm-tree flower flower-outline flower-tulip flower-tulip-outline flower-poppy
        flower-pollen sprout sprout-outline leaf leaf-maple leaf-circle leaf-circle-outline clover grass cactus mushroom mushroom-outline
        flower-lotus nature nature-people sunflower seed seed-outline terrain mountain image-filter-hdr image-filter-hdr-outline waves
        water water-outline water-opacity water-percent fire fire-circle volcano island wave bonfire campfire rocket-launch earth earth-box
        planet moon-new moon-full moon-waning-crescent moon-waxing-crescent moon-first-quarter moon-last-quarter weather-sunny weather-night
        star star-outline orbit orbit-variant satellite satellite-variant satellite-uplink space-station creation shimmer weather-sunset
        weather-sunset-up weather-sunset-down sun-wireless white-balance-sunny telescope atom molecule diamond-stone dna crystal-ball
        shell shell-outline leaf-off tortoise fireplace rock bee-flower pine-tree-fire forest forest-outline carrot""",
    "Animals": """cat dog dog-side bird duck fish fishbowl fishbowl-outline pig pig-variant panda rabbit rabbit-variant turtle snake spider
        spider-thread owl penguin paw paw-outline paw-off bug bug-outline bee butterfly butterfly-outline ladybug jellyfish jellyfish-outline
        elephant-outline kangaroo koala shark shark-fin bat bird dolphin cow cow-off horse horse-variant horse-variant-fast sheep
        snail teddy-bear ghost ghost-outline ghost-off unicorn unicorn-variant rodent bullseye-arrow bone fish-off pidgey rabbit-variant-outline
        dog-service bird-off pig-variant-outline cat-outline dog-outline""",
    "Weather": """weather-sunny weather-night weather-sunny-off weather-cloudy weather-partly-cloudy weather-night-partly-cloudy weather-rainy
        weather-pouring weather-snowy weather-snowy-heavy weather-snowy-rainy weather-lightning weather-lightning-rainy weather-fog
        weather-hazy weather-windy weather-windy-variant weather-tornado weather-hurricane weather-hail weather-sunset weather-sunset-up
        weather-sunset-down weather-cloudy-alert weather-cloudy-arrow-right weather-tornado white-balance-sunny moon-waning-crescent
        moon-waxing-crescent moon-full moon-new snowflake snowflake-alert snowflake-variant snowman thermometer thermometer-high
        thermometer-low umbrella umbrella-outline cloud cloud-outline rainbow lightning-bolt flash flash-outline water water-outline
        waves wind-turbine fire sun-thermometer sun-snowflake cloud-lock-outline weather-snowy-heavy tsunami earth-box""",
    "Objects": """lightbulb lightbulb-on lightbulb-outline lightbulb-on-outline flashlight flashlight-off lamp bell bell-outline clock clock-outline
        alarm alarm-check alarm-plus alarm-off watch smart-card book book-outline book-open book-open-variant book-open-page-variant bookshelf
        book-plus notebook notebook-outline script script-outline script-text scroll-text newspaper newspaper-variant-outline calendar
        calendar-outline calendar-star calendar-check calendar-today calendar-month map map-outline map-marker map-marker-radius compass
        compass-rose binoculars magnify telescope key key-variant key-outline key-chain lock padlock gift gift-outline balloon party-popper
        bell-ring trophy-award sunglasses glasses hat-fedora hanger shoe-formal shoe-heel shoe-sneaker tshirt-crew tshirt-v bag-personal
        bag-suitcase bag-carry-on bag-checked bag-personal-outline briefcase briefcase-outline backpack hammer wrench axe pickaxe shovel saw-blade
        anvil toolbox ladder scissors-cutting content-cut ruler pencil pen pencil-box marker marker-cancel brush paperclip pin
        magnet magnet-on bomb nuke candle campfire flask flask-empty flask-outline test-tube beaker beaker-outline microscope atom
        rocket rocket-launch telescope satellite-variant ufo robot robot-vacuum robot-industrial robot-mower toy-brick toy-brick-outline
        toy-brick-plus puzzle teddy-bear baseball-bat bowling billiards dice-multiple cards-playing-outline cards-playing gamepad-variant
        controller-classic video-vintage television television-classic radio radio-tower speaker headphones camera camera-outline phone
        phone-outline cellphone laptop monitor printer-3d mailbox mailbox-outline envelope email package-variant parachute anchor ship-wheel
        sail-boat pirate eye-circle bottle-wine bottle-soda bottle-water beer coffee coffee-outline cup cup-water glass-cocktail glass-mug
        silverware-fork-knife silverware spoon-sugar umbrella flag flag-outline flag-variant mailbox-up fire-extinguisher flash-red-eye
        handcuffs lighter zippo gas-cylinder oil barrel tent home-city home-city-outline castle bank domain office-building cart-heart
        door-open door-closed door window-closed-variant window-open-variant stairs elevator-passenger bed bed-outline sofa toilet shower
        lamp-outline ceiling-light floor-lamp chair-rolling dumbbell kettlebell hot-tub spa spa-outline mirror hand-saw toaster""",
    "Food": """food-apple food-apple-outline food-drumstick food-drumstick-outline food-steak food-turkey food-croissant food-hot-dog
        food-variant food fruit-cherries fruit-citrus fruit-grapes fruit-grapes-outline fruit-pear fruit-pineapple fruit-watermelon fruit-pear
        carrot corn mushroom pepper-hot peanut cookie cookie-outline cake cake-variant cake-variant-outline cupcake candy candy-outline
        candycane ice-cream ice-pop pizza hamburger noodles rice sausage taco egg egg-outline egg-fried bread-slice bread-slice-outline
        cheese french-fries pasta pot-steam pot-mix coffee coffee-outline coffee-maker tea tea-outline cup cup-outline cup-water
        glass-cocktail glass-wine glass-mug glass-mug-variant glass-pint-outline glass-stange beer beer-outline bottle-wine bottle-soda
        bottle-water bottle-tonic silverware silverware-fork-knife silverware-fork silverware-spoon baguette barley bowl bowl-mix
        fish food-fork-drink food-halal food-off food-takeout-box food-takeout-box-outline cow chef-hat popcorn muffin waffle""",
    "Sport": """soccer soccer-field basketball basketball-hoop football football-helmet baseball baseball-bat tennis tennis-ball volleyball
        rugby hockey-puck hockey-sticks golf golf-tee bowling billiards table-tennis badminton boxing-glove karate kabaddi fencing
        weight-lifter dumbbell kettlebell run run-fast walk bike swim ski snowboard skateboard skateboarding roller-skate kayaking sail-boat
        rowing hiking jump-rope yoga whistle whistle-outline stadium scoreboard podium podium-gold podium-silver podium-bronze trophy
        trophy-outline trophy-award trophy-variant medal medal-outline flag-checkered timer timer-outline stopwatch racing-helmet go-kart
        car-sports motorbike bullseye-arrow arrow-projectile bow-arrow target dart cricket curling diving mixed-martial-arts
        paragliding polo handball gymnastics biathlon sledding horseshoe soccer-field heart-pulse meditation""",
    "Emoji": """emoticon emoticon-outline emoticon-happy emoticon-happy-outline emoticon-sad emoticon-sad-outline emoticon-neutral
        emoticon-neutral-outline emoticon-cool emoticon-cool-outline emoticon-wink emoticon-wink-outline emoticon-angry emoticon-angry-outline
        emoticon-cry emoticon-cry-outline emoticon-excited emoticon-excited-outline emoticon-kiss emoticon-kiss-outline emoticon-lol
        emoticon-lol-outline emoticon-confused emoticon-confused-outline emoticon-dead emoticon-dead-outline emoticon-devil emoticon-devil-outline
        emoticon-frown emoticon-frown-outline emoticon-sick emoticon-sick-outline emoticon-tongue emoticon-tongue-outline emoticon-poop
        emoticon-poop-outline sticker-emoji emoticon-wink emoticon-happy robot-happy robot-angry robot-confused robot-dead robot-love
        ghost ghost-outline skull skull-outline heart heart-outline heart-broken heart-circle heart-flash heart-half-full hand-peace hand-okay
        hand-clap thumb-up thumb-down fire star party-popper balloon cake-variant gift""",
    "Travel": """car car-side car-sports car-hatchback car-estate car-pickup car-convertible car-electric car-battery car-light-high
        taxi bus bus-school bus-double-decker truck truck-delivery truck-fast truck-trailer van-passenger van-utility tractor
        motorbike motorbike-electric bike scooter moped train train-car tram subway subway-variant airplane airplane-takeoff
        airplane-landing airplane-off helicopter ferry sail-boat ship-wheel anchor rowing kayaking rocket rocket-launch rocket-outline
        ufo parachute tank rv-truck forklift excavator bulldozer crane tow-truck ambulance fire-truck police-badge road road-variant
        highway traffic-light traffic-cone gas-station ev-station map-marker map-marker-radius map-marker-path navigation navigation-variant
        compass compass-outline compass-rose earth earth-box map map-outline map-search directions sign-direction sign-caution
        flag-checkered garage garage-open parking tent van-passenger bridge city city-variant domain castle lighthouse windmill ferris-wheel
        gondola lifebuoy hot-air-balloon paper-airplane paper-airplane-outline airballoon""",
    "Time": """clock clock-outline clock-fast clock-alert clock-alert-outline clock-check clock-check-outline clock-start clock-end clock-in
        clock-out clock-time-eight clock-time-four clock-time-twelve clock-time-three clock-time-six clock-time-nine alarm alarm-bell
        alarm-check alarm-plus alarm-multiple alarm-off alarm-snooze timer timer-outline timer-off timer-off-outline timer-sand
        timer-sand-empty timer-sand-full timer-sand-complete timer-play timer-pause timer-stop timer-refresh timer-cog timer-plus
        timer-minus timer-10 timer-3 stopwatch hourglass hourglass-empty hourglass-full hourglass-end hourglass-start hourglass-outline
        history update calendar calendar-outline calendar-today calendar-check calendar-clock calendar-star calendar-month calendar-week
        calendar-heart calendar-blank calendar-range calendar-plus calendar-minus calendar-remove calendar-alert calendar-refresh
        calendar-sync watch watch-vibrate sun-clock progress-clock moon-waning-crescent weather-sunset speedometer speedometer-slow
        speedometer-medium gauge gauge-low gauge-full""",
    "Math": """plus minus plus-thick minus-thick plus-minus plus-minus-variant multiplication multiplication-box divide equal equal-box
        percent percent-outline less-than less-than-or-equal greater-than greater-than-or-equal not-equal infinity function function-variant
        sigma pi pi-box square-root sqrt numeric numeric-0 numeric-1 numeric-2 numeric-3 numeric-4 numeric-5 numeric-6 numeric-7 numeric-8
        numeric-9 numeric-0-box numeric-1-box numeric-2-box numeric-3-box numeric-4-box numeric-5-box numeric-6-box numeric-7-box numeric-8-box
        numeric-9-box numeric-0-circle numeric-1-circle numeric-2-circle numeric-3-circle numeric-4-circle numeric-5-circle numeric-6-circle
        numeric-7-circle numeric-8-circle numeric-9-circle numeric-0-circle-outline numeric-1-circle-outline numeric-2-circle-outline
        numeric-3-circle-outline numeric-4-circle-outline numeric-5-circle-outline numeric-6-circle-outline numeric-7-circle-outline
        numeric-8-circle-outline numeric-9-circle-outline numeric-positive-1 numeric-negative-1 pound pound-box exclamation
        calculator calculator-variant abacus ruler ruler-square compass-outline math-compass triangle-outline angle-acute angle-right
        angle-obtuse set-all set-none set-center vector-intersection vector-union vector-difference ceiling-light matrix variable
        alpha-a-circle alpha-b-circle alpha-x-circle alpha-y-circle alpha-a-box alpha-b-box alpha-x-box alpha-y-box
        alpha-a alpha-b alpha-c alpha-d alpha-e alpha-f alpha-g alpha-h alpha-i alpha-j alpha-k alpha-l alpha-m alpha-n alpha-o alpha-p
        alpha-q alpha-r alpha-s alpha-t alpha-u alpha-v alpha-w alpha-x alpha-y alpha-z""",
}

# Whole MDI categories that are curated in bulk (variants such as -outline/-off/-multiple are skipped, the explicit lists add the
# useful ones back). Maps the MDI category -> the app category.
BULK = {
    "Gaming / RPG": "Game",
    "Arrow": "Arrows",
    "Shape": "Shapes",
    "Emoji": "Emoji",
    "Animal": "Animals",
    "Nature": "Nature",
    "Holiday": "Objects",
    "Currency": "Shop & Money",
    "Shopping": "Shop & Money",
    "Hardware / Tools": "Objects",
    "Music": "Audio",
    "Audio": "Audio",
    "Weather": "Weather",
    "Sport": "Sport",
    "Food / Drink": "Food",
    "Medical / Hospital": "People",
    "Science": "Objects",
    "Clothing": "Objects",
    "Transportation + Flying": "Travel",
    "Transportation + Other": "Travel",
    "Places": "Travel",
    "Notification": "Status",
}
BULK_BAD = set("""outline multiple box off marker bluetooth wireless network cog settings edit remove lock search minus check alert
sync clock refresh account file folder database cellphone phone card credit book archive content message bell cookie filter
movie table store timer wifi eye keyboard office airplane car printer router microphone head video image camera application window
puzzle transfer pause play stop variant double triple left right up down plus circle cards expansion lambda hololens microsoft
nintendo origin steam xbox playstation nuxt vuejs google facebook twitter github""".split())
# Bulk categories that are still too big even after the variant filter: keep at most N (alphabetical after explicit names)
BULK_LIMIT = {"Sport": 0, "Food / Drink": 0, "Weather": 0, "Transportation + Other": 0, "Places": 0, "Transportation + Flying": 0,
              "Medical / Hospital": 0, "Science": 12, "Clothing": 10, "Hardware / Tools": 30, "Holiday": 30, "Currency": 18,
              "Shopping": 14, "Notification": 0, "Music": 0, "Audio": 0, "Gaming / RPG": 90, "Arrow": 0, "Shape": 40, "Emoji": 0,
              "Animal": 40, "Nature": 40}

# Hand-made extras for game staples that MDI lacks (24x24, y down, fill only; a hole is a second sub-path drawn the other way).
EXTRAS = [
    {"i": "coin", "c": "Shop & Money", "t": "money gold cash currency token reward gem pickup collect",
     "p": "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2ZM12 4.5A7.5 7.5 0 1 1 12 19.5A7.5 7.5 0 1 1 12 4.5ZM10.8 7.5H13.2V16.5H10.8Z"},
    {"i": "coin-solid", "c": "Shop & Money", "t": "money gold cash currency token reward pickup collect disc",
     "p": "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2Z"},
    {"i": "coin-stack", "c": "Shop & Money", "t": "coins money gold cash savings treasure pile stack bank",
     "p": "M12 3C7.6 3 4 4.3 4 6S7.6 9 12 9S20 7.7 20 6S16.4 3 12 3ZM4 8.5V11.5C4 13.2 7.6 14.5 12 14.5S20 13.2 20 11.5V8.5C18.4 9.9 15.4 10.5 12 10.5S5.6 9.9 4 8.5ZM4 14V17C4 18.7 7.6 20 12 20S20 18.7 20 17V14C18.4 15.4 15.4 16 12 16S5.6 15.4 4 14Z"},
    {"i": "gem", "c": "Game", "t": "jewel crystal diamond gemstone ruby precious treasure premium currency",
     "p": "M6 3H18L22 9L12 21L2 9ZM9.2 9L12 16.5L14.8 9Z"},
    {"i": "gem-facet", "c": "Game", "t": "jewel crystal diamond gemstone precious treasure premium currency cut",
     "p": "M6 3H18L22 9L12 21L2 9ZM7.3 8.2H10.4L12 5.2L13.6 8.2H16.7L12 17.4Z"},
    {"i": "joystick", "c": "Controls", "t": "controller gamepad stick arcade move control dpad",
     "p": "M12 2A4 4 0 1 1 12 10A4 4 0 1 1 12 2ZM11 9.5H13V15H11ZM4 15.5H20A2 2 0 0 1 22 17.5V20A2 2 0 0 1 20 22H4A2 2 0 0 1 2 20V17.5A2 2 0 0 1 4 15.5Z"},
    {"i": "potion", "c": "Game", "t": "bottle elixir health mana flask magic drink heal",
     "p": "M9 2H15V4H14V8.2C16.9 9.4 19 12.2 19 15.5C19 19.6 15.9 22 12 22C8.1 22 5 19.6 5 15.5C5 12.2 7.1 9.4 10 8.2V4H9ZM8 14.5C8 17.2 9.8 19 12 19C14.2 19 16 17.2 16 14.5Z"},
    {"i": "heart-pixel", "c": "Game", "t": "life health hp retro pixel lives love like",
     "p": "M4 4H9V6H11V4H16V6H18V12H16V14H14V16H12V18H10V16H8V14H6V12H4V6Z"},
    {"i": "star-burst", "c": "Game", "t": "bonus rating reward spark win favorite level shine",
     "p": "M12 1L14.6 8.4L22 6L17.2 12L22 18L14.6 15.6L12 23L9.4 15.6L2 18L6.8 12L2 6L9.4 8.4Z"},
    {"i": "arrow-up-triangle", "c": "Arrows", "t": "up move jump north dpad direction",
     "p": "M12 3L22 19H2Z"},
    {"i": "arrow-down-triangle", "c": "Arrows", "t": "down move south dpad direction crouch",
     "p": "M12 21L2 5H22Z"},
    {"i": "arrow-left-triangle", "c": "Arrows", "t": "left move west dpad direction back previous",
     "p": "M3 12L19 2V22Z"},
    {"i": "arrow-right-triangle", "c": "Arrows", "t": "right move east dpad direction next play forward",
     "p": "M21 12L5 2V22Z"},
    {"i": "dpad", "c": "Controls", "t": "directional pad arrows controller cross move gamepad",
     "p": "M9 2H15V9H22V15H15V22H9V15H2V9H9Z"},
    {"i": "button-a", "c": "Controls", "t": "gamepad action fire jump press circle controller",
     "p": "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2ZM12 5A7 7 0 1 1 12 19A7 7 0 1 1 12 5Z"},
    {"i": "sparkle", "c": "Game", "t": "magic shine glitter new star effect twinkle",
     "p": "M12 2C12.6 7.4 16.6 11.4 22 12C16.6 12.6 12.6 16.6 12 22C11.4 16.6 7.4 12.6 2 12C7.4 11.4 11.4 7.4 12 2Z"},
]

# --------------------------------------------------------------------------------------------- search synonyms
# token (a word of an icon name or alias) -> extra search words. Every icon whose name contains the token gets them.
SYN_TOKEN = {
    "heart": "love like life lives health hp favorite favourite romance",
    "star": "favorite favourite rating rate score bonus level rank",
    "check": "tick ok yes done correct accept confirm right valid success",
    "close": "x cross no cancel wrong delete remove exit dismiss quit",
    "cross": "x close no wrong",
    "volume": "sound audio speaker loud sfx noise",
    "mute": "silent quiet sound off no audio",
    "music": "song melody tune sound soundtrack bgm",
    "note": "music",
    "pause": "wait hold break halt",
    "play": "start run begin go resume",
    "stop": "end halt square finish",
    "cog": "settings gear options preferences config setup",
    "cogs": "settings gear options preferences config setup",
    "home": "house main menu start base",
    "trophy": "win winner victory cup prize award achievement champion",
    "medal": "win award achievement rank prize badge",
    "crown": "king queen royal vip premium rank",
    "sword": "weapon attack fight blade battle melee",
    "axe": "weapon attack tool wood battle",
    "bow": "weapon archer shoot arrow ranged",
    "shield": "defense defence protect armor armour guard safe block",
    "gamepad": "controller game play joystick console",
    "controller": "gamepad game play joystick console",
    "timer": "time clock countdown stopwatch wait",
    "clock": "time hour watch",
    "hourglass": "time wait loading sand",
    "arrow": "direction pointer move go",
    "account": "user player profile avatar person login",
    "human": "person people man player",
    "skull": "death dead die danger enemy poison kill game over",
    "ghost": "enemy spirit halloween spooky monster",
    "bomb": "explosion explode danger boom enemy tnt",
    "lock": "locked secure private password closed unavailable",
    "key": "unlock access password open",
    "door": "exit enter room open close",
    "exit": "quit leave logout door out",
    "logout": "exit leave quit sign out",
    "login": "enter sign in join",
    "cart": "shop shopping buy store basket purchase",
    "shopping": "shop buy store cart purchase",
    "store": "shop market buy sell",
    "gift": "present reward bonus surprise box",
    "cash": "money coin price pay buy dollar currency",
    "currency": "money coin price pay cash",
    "wallet": "money pay cash coins purse",
    "diamond": "gem jewel crystal premium currency",
    "crystal": "gem jewel diamond magic",
    "treasure": "chest loot reward gold box",
    "chest": "treasure loot reward box",
    "sack": "bag loot inventory items",
    "bag": "inventory items loot backpack",
    "backpack": "inventory items bag",
    "bottle": "potion drink health mana",
    "flask": "potion lab science magic mana health",
    "fire": "flame burn hot attack lava",
    "flash": "lightning power energy electric speed thunder",
    "lightning": "flash power energy electric speed thunder bolt",
    "bolt": "lightning flash power energy electric speed",
    "rocket": "space launch fast boost missile ship",
    "ufo": "alien space ship saucer",
    "alien": "space monster enemy ufo",
    "robot": "bot machine enemy ai",
    "bug": "insect enemy error debug",
    "flag": "goal finish checkpoint banner mark",
    "map": "world level location navigation",
    "compass": "direction navigation north explore",
    "target": "aim goal crosshair shoot bullseye",
    "crosshairs": "aim target shoot sight",
    "bullseye": "aim target goal dart",
    "eye": "view see visible show look",
    "magnify": "search find zoom look glass",
    "plus": "add new increase more create",
    "minus": "remove subtract decrease less",
    "refresh": "reload restart retry again update",
    "restart": "reload retry again replay",
    "reload": "refresh restart retry again",
    "sync": "refresh reload update",
    "undo": "back revert cancel",
    "redo": "forward repeat",
    "repeat": "loop again replay",
    "shuffle": "random mix",
    "skip": "next previous track jump",
    "fast": "speed forward quick",
    "rewind": "back previous reverse",
    "menu": "hamburger list navigation options",
    "dots": "more menu options",
    "information": "info about help details",
    "help": "question info support hint",
    "alert": "warning danger error attention caution",
    "bell": "notification alarm alert ring reminder",
    "share": "send social post",
    "download": "save get install",
    "upload": "send publish",
    "delete": "trash remove bin erase",
    "trash": "delete remove bin garbage",
    "pencil": "edit write draw change",
    "content": "copy save paste",
    "save": "store disk keep",
    "camera": "photo picture screenshot",
    "image": "picture photo gallery",
    "link": "chain url connect",
    "thumb": "like vote rate",
    "emoticon": "emoji face smiley mood",
    "weather": "climate forecast",
    "sunny": "sun day clear",
    "night": "moon dark evening",
    "cloud": "sky weather online server",
    "snowflake": "winter cold ice freeze",
    "water": "drop liquid sea rain",
    "tree": "forest plant nature wood",
    "leaf": "plant nature green eco",
    "flower": "plant nature garden bloom",
    "paw": "animal pet cat dog foot",
    "fish": "animal sea water pet",
    "bird": "animal fly wings",
    "cat": "animal pet kitten",
    "dog": "animal pet puppy",
    "dice": "random chance luck gamble board game",
    "cards": "poker deck game casino playing",
    "puzzle": "piece game jigsaw solve",
    "chess": "board game strategy",
    "ladder": "climb up platformer",
    "castle": "fortress kingdom building tower",
    "tank": "vehicle war military",
    "pistol": "gun weapon shoot",
    "knife": "weapon blade",
    "magic": "wizard spell sorcery mana",
    "wizard": "magic mage spell witch",
    "staff": "magic wand weapon",
    "run": "sprint move fast player",
    "walk": "move player step",
    "jump": "hop leap player",
    "swim": "water player",
    "medal": "badge award",
    "ticket": "pass coupon entry",
    "tag": "label price",
    "percent": "discount sale offer",
    "sale": "discount offer price",
    "piggy": "bank savings money coins",
    "battery": "power energy charge level",
    "power": "on off energy switch",
    "wifi": "internet network connection online",
    "bluetooth": "connection wireless",
    "signal": "network connection reception",
    "settings": "gear options preferences config setup",
    "tune": "settings sliders adjust equalizer options",
    "filter": "funnel sort search",
    "sort": "order arrange list",
    "fullscreen": "expand maximize screen",
    "pin": "location mark attach",
    "bookmark": "save favorite mark",
    "book": "read library story guide manual",
    "script": "scroll quest paper story",
    "calendar": "date day schedule event",
    "chart": "graph stats statistics data",
    "trending": "graph stats growth",
    "gauge": "meter speedometer level",
    "speedometer": "speed meter gauge",
    "email": "mail message letter",
    "message": "chat text talk comment",
    "chat": "message talk comment bubble",
    "send": "submit message paper plane",
    "phone": "call mobile",
    "cellphone": "phone mobile device",
    "tablet": "device screen",
    "monitor": "screen display computer",
    "keyboard": "keys type input",
    "mouse": "cursor pointer input",
    "cursor": "pointer mouse tap click",
    "gesture": "touch finger tap swipe",
    "hand": "touch finger gesture",
    "fingerprint": "touch id security",
    "palette": "color paint art",
    "brush": "paint draw art",
    "layers": "stack levels depth z order",
    "grid": "tiles squares layout",
    "square": "box rectangle shape",
    "circle": "round dot shape",
    "triangle": "shape arrow",
    "hexagon": "shape cell honeycomb",
    "pentagon": "shape",
    "octagon": "shape stop sign",
    "ellipse": "oval shape",
    "cube": "3d box shape block",
    "sphere": "ball 3d shape",
    "checkerboard": "chess pattern tiles",
    "atom": "science physics",
    "dna": "science genetic",
    "magnet": "attract pull physics",
    "lightbulb": "idea hint tip light",
    "candle": "light fire flame",
    "tent": "camp outdoor",
    "hammer": "tool build smash",
    "wrench": "tool repair fix settings",
    "screwdriver": "tool fix",
    "toolbox": "tools repair kit",
    "anchor": "ship sea harbor",
    "sail": "boat ship sea",
    "ship": "boat sea vessel",
    "airplane": "plane flight fly",
    "helicopter": "fly vehicle",
    "car": "vehicle drive auto",
    "bus": "vehicle transport",
    "truck": "vehicle delivery",
    "train": "vehicle rail",
    "bike": "bicycle vehicle",
    "motorbike": "vehicle motorcycle",
    "soccer": "football ball sport",
    "football": "american ball sport",
    "basketball": "ball sport",
    "tennis": "ball sport racket",
    "baseball": "ball sport bat",
    "bowling": "ball sport",
    "golf": "ball sport",
    "boxing": "fight sport glove punch",
    "karate": "fight martial sport",
    "food": "eat meal hunger",
    "apple": "fruit food health",
    "cake": "birthday dessert sweet",
    "pizza": "food fast eat",
    "hamburger": "food fast eat burger",
    "coffee": "drink cup cafe",
    "beer": "drink bar",
    "candy": "sweet sugar",
    "cookie": "sweet snack biscuit",
    "ice": "cream dessert cold",
    "egg": "food chicken",
    "carrot": "vegetable food",
    "pepper": "spicy hot food",
    "party": "celebrate confetti win",
    "balloon": "party celebrate",
    "pumpkin": "halloween",
    "snowman": "winter christmas",
    "christmas": "xmas winter holiday",
    "halloween": "spooky horror",
    "hospital": "medical health",
    "pill": "medicine health",
    "stethoscope": "doctor medical",
    "needle": "medicine injection",
    "bandage": "heal health first aid",
    "medical": "health hospital",
    "virus": "infection germ",
    "baby": "child kid",
    "face": "avatar person head",
    "tooth": "dental health",
    "brain": "mind think smart",
    "bone": "skeleton health",
    "orbit": "space planet satellite",
    "planet": "space world",
    "moon": "night space lunar",
    "satellite": "space orbit",
    "telescope": "space look observe",
    "earth": "world globe planet global",
    "web": "internet world browser",
    "translate": "language world",
    "code": "programming script develop",
    "console": "terminal log command",
    "database": "data storage server",
    "server": "data host cloud",
    "folder": "directory files",
    "file": "document paper",
    "clipboard": "copy paste list",
    "printer": "print paper",
    "scanner": "scan",
    "qrcode": "scan code barcode",
    "barcode": "scan code",
    "format": "text style",
    "text": "words type write",
    "window": "app screen",
    "application": "app program",
    "android": "phone robot os",
    "github": "code git",
    "language": "code programming",
    "toggle": "switch on off",
    "checkbox": "tick select option",
    "radiobox": "select option",
    "drag": "move handle reorder",
    "resize": "scale size",
    "rotate": "turn spin",
    "flip": "mirror turn",
    "swap": "exchange switch trade",
    "transfer": "move send",
    "history": "recent time past undo",
    "update": "refresh new version",
    "upgrade": "improve level up better",
    "new": "fresh latest release",
    "vibrate": "haptic shake",
    "brightness": "light screen sun",
    "invert": "colors negative",
    "theme": "dark light mode",
    "headphones": "audio music listen",
    "headset": "audio mic chat",
    "microphone": "mic voice record speak",
    "speaker": "audio sound loud",
    "equalizer": "audio sound levels",
    "waveform": "audio sound wave",
    "guitar": "music instrument",
    "piano": "music instrument keys",
    "violin": "music instrument",
    "trumpet": "music instrument",
    "radio": "music broadcast",
    "disc": "music cd album",
    "record": "rec capture audio video",
    "bullhorn": "announce megaphone shout",
    "badge": "award rank id",
    "police": "law cop officer",
    "ninja": "stealth fighter assassin",
    "pirate": "ship sea skull treasure",
    "pac": "arcade retro game",
    "space": "galaxy cosmos star",
    "invaders": "space arcade retro game",
    "pokeball": "catch monster game",
    "boomerang": "weapon throw",
    "mace": "weapon club",
    "spear": "weapon lance",
    "coin": "money gold cash token currency",
    "dollar": "money currency usd",
    "usd": "dollar money currency",
    "eur": "euro money currency",
    "bitcoin": "crypto money currency",
    "bank": "money finance savings",
    "safe": "vault money security",
    "handshake": "deal agreement partner",
    "tournament": "bracket competition match",
    "podium": "rank place winner",
    "scoreboard": "score rank points",
    "whistle": "referee sport",
    "stadium": "arena sport field",
    "field": "pitch sport",
    "snail": "slow animal",
    "bee": "insect animal honey",
    "butterfly": "insect animal",
    "spider": "insect animal web",
    "turtle": "animal slow",
    "snake": "animal reptile",
    "owl": "bird animal night",
    "penguin": "bird animal cold",
    "duck": "bird animal water",
    "pig": "animal farm",
    "cow": "animal farm",
    "horse": "animal ride",
    "sheep": "animal farm wool",
    "rabbit": "animal bunny",
    "panda": "animal bear",
    "unicorn": "animal horse magic",
    "elephant": "animal big",
    "shark": "animal sea danger",
    "dolphin": "animal sea",
    "jellyfish": "animal sea",
    "kangaroo": "animal jump",
    "koala": "animal bear",
    "bat": "animal night halloween",
    "cactus": "plant desert",
    "mushroom": "plant forest toadstool",
    "sprout": "plant grow seed new",
    "grass": "plant lawn",
    "clover": "luck plant",
    "volcano": "lava mountain fire",
    "mountain": "terrain peak",
    "terrain": "mountain land",
    "island": "sea beach",
    "wave": "sea water",
    "rainbow": "weather color",
    "umbrella": "rain weather",
    "thermometer": "temperature weather hot cold",
    "tornado": "weather storm",
    "hurricane": "weather storm",
    "fog": "weather mist",
    "windy": "weather wind",
    "hail": "weather ice",
    "snowy": "weather snow winter",
    "rainy": "weather rain",
    "pouring": "weather rain heavy",
    "cloudy": "weather clouds",
    "lightning-bolt": "energy power",
}
# exact icon name -> extra words
SYN_NAME = {
    "heart": "love like life lives health hp favorite favourite",
    "heart-outline": "love like life lives health hp favorite empty",
    "heart-broken": "dead lost life game over hurt",
    "heart-half-full": "life health hp half damage",
    "star": "favorite rating score bonus level rank",
    "check": "tick ok yes done correct accept confirm",
    "close": "x cross no cancel wrong delete remove exit",
    "volume-high": "sound audio speaker loud on sfx",
    "volume-off": "sound audio mute silent speaker off",
    "volume-mute": "sound audio mute silent speaker off",
    "music-note": "song melody tune sound bgm",
    "music-off": "mute no music silent",
    "exit-run": "quit leave out door run escape",
    "exit-to-app": "quit leave out door logout",
    "cog": "settings gear options preferences config",
    "home": "house main menu start",
    "trophy": "win winner victory cup prize award achievement",
    "diamond-stone": "gem jewel crystal premium currency",
    "bottle-tonic": "potion health mana elixir",
    "bottle-tonic-plus": "potion health heal heal-potion",
    "bottle-tonic-skull": "poison potion danger",
    "treasure-chest": "loot reward gold box",
    "lightning-bolt": "energy power speed electric flash thunder",
    "fire": "flame burn hot",
    "skull": "death dead die danger enemy poison game over kill",
    "controller-classic": "retro gamepad nintendo console",
    "gamepad-variant": "controller game play joystick console",
    "arrow-up": "up north jump top",
    "arrow-down": "down south bottom",
    "arrow-left": "left west back previous",
    "arrow-right": "right east forward next",
    "chevron-left": "back previous left",
    "chevron-right": "next forward right",
    "chevron-up": "up expand collapse top",
    "chevron-down": "down expand collapse bottom dropdown",
    "plus": "add new more increase create",
    "minus": "remove less decrease subtract",
    "refresh": "reload restart retry again",
    "restart": "reload retry again replay",
    "play": "start run go resume",
    "pause": "wait hold break halt",
    "stop": "end halt square",
    "play-circle": "start run go resume video",
    "pause-circle": "wait hold break halt",
    "stop-circle": "end halt square",
    "skip-next": "next forward track",
    "skip-previous": "previous back track",
    "fast-forward": "speed forward faster",
    "rewind": "back reverse",
    "magnify": "search find zoom",
    "delete": "trash remove bin erase",
    "pencil": "edit write draw",
    "lock": "locked secure private password closed unavailable",
    "lock-open": "unlocked open available unlock",
    "key": "unlock access password",
    "bell": "notification alarm alert ring",
    "information": "info about help details",
    "help-circle": "question hint support",
    "alert": "warning danger error attention caution",
    "alert-circle": "warning danger error attention caution",
    "thumb-up": "like good ok vote yes",
    "thumb-down": "dislike bad vote no",
    "account": "user player profile avatar person",
    "account-circle": "user player profile avatar person",
    "account-group": "players team friends multiplayer",
    "account-multiple": "players team friends multiplayer",
    "cart": "shop shopping buy store basket",
    "store": "shop market buy sell",
    "gift": "present reward bonus surprise",
    "timer": "time countdown stopwatch clock",
    "timer-sand": "time wait loading hourglass",
    "clock": "time hour watch",
    "sword": "weapon attack fight blade battle",
    "sword-cross": "weapons battle fight attack versus pvp",
    "shield": "defense protect armor guard safe",
    "crown": "king queen royal vip premium rank leader",
    "rocket": "space launch fast boost missile",
    "flag-checkered": "finish race goal end line",
    "flag": "goal checkpoint banner",
    "target": "aim goal crosshair shoot",
    "crosshairs": "aim target shoot sight",
    "bomb": "explosion explode danger boom tnt",
    "ghost": "enemy spirit halloween spooky",
    "robot": "bot machine enemy ai",
    "ufo": "alien space ship saucer",
    "emoticon-happy": "smile smiley happy face emoji good",
    "emoticon-sad": "sad unhappy face emoji bad",
    "magic-staff": "wand spell wizard mage weapon",
    "wizard-hat": "mage witch magic spell",
    "dice-6": "random chance luck roll die",
    "cards-playing": "poker deck casino",
    "puzzle": "piece jigsaw solve",
    "map-marker": "location pin place gps",
    "fullscreen": "expand maximize screen",
    "fullscreen-exit": "shrink minimize screen",
    "menu": "hamburger list navigation options",
    "dots-vertical": "more menu options",
    "dots-horizontal": "more menu options",
    "share-variant": "send social post",
    "download": "save get install",
    "upload": "send publish",
    "content-save": "disk store keep",
    "wifi": "internet network connection online",
    "battery": "power energy charge",
    "power": "on off energy switch shutdown",
    "gesture-tap": "touch finger click press",
    "gesture-swipe": "touch finger slide drag",
    "hand-pointing-up": "touch finger tap click",
    "cursor-default": "pointer mouse arrow",
    "cursor-pointer": "mouse hand click",
}

ALIAS_STOP = {"outline", "variant"}
CAT_HINTS = {"gaming", "music", "audio", "arrow", "sport", "weather", "food", "animal", "nature", "shape", "currency", "shopping",
             "emoji", "holiday", "science", "medical", "clothing", "settings", "lock", "navigation", "notification"}



# --------------------------------------------------------------------------------------------- game-icons (game-icons.net)
# (app category, [(keyword, how many), ...]); icons whose id has the keyword as a whole word, most basic (shortest) first.
GAME_SPEC = [
    ("Weapons", [("sword", 9), ("broadsword", 1), ("dagger", 4), ("axe", 6), ("spear", 3), ("mace", 3), ("hammer", 4), ("bow", 2), ("crossbow", 1),
                 ("arrow", 4), ("bomb", 6), ("grenade", 3), ("dynamite", 1), ("pistol", 2), ("rifle", 2), ("cannon", 3), ("shuriken", 3),
                 ("boomerang", 2), ("slingshot", 1), ("missile", 2), ("rocket", 3), ("knife", 3), ("katana", 1), ("scythe", 1), ("trident", 1),
                 ("halberd", 1), ("flail", 1), ("whip", 1), ("laser", 3), ("bullet", 3), ("ammo", 1), ("gun", 3), ("explosion", 2), ("landmine", 1)]),
    ("Armor", [("shield", 8), ("helmet", 6), ("breastplate", 1), ("armor", 4), ("gauntlet", 1), ("boots", 2), ("cape", 1), ("belt", 1), ("visor", 1),
               ("bracer", 1), ("shoulder", 1), ("pauldron", 1), ("greaves", 1)]),
    ("Treasure", [("crown", 5), ("coin", 2), ("coins", 3), ("gem", 3), ("gems", 1), ("diamond", 3), ("crystal", 4), ("chest", 5), ("treasure", 2),
                  ("trophy", 3), ("medal", 3), ("ring", 3), ("necklace", 2), ("emerald", 1), ("ruby", 1), ("pearl", 1), ("gold", 3), ("jewel", 2),
                  ("sack", 1), ("money", 2), ("cash", 2), ("banknote", 1), ("tiara", 1), ("ticket", 1)]),
    ("Magic", [("potion", 5), ("flask", 3), ("wand", 3), ("staff", 3), ("orb", 3), ("spell", 3), ("scroll", 3), ("book", 5), ("rune", 3), ("portal", 2),
               ("magic", 4), ("amulet", 1), ("talisman", 1), ("pentagram", 1), ("crystal-ball", 1), ("cauldron", 1), ("lightning", 4), ("sparkles", 1)]),
    ("Monsters", [("skull", 6), ("bone", 2), ("ghost", 3), ("zombie", 2), ("vampire", 2), ("demon", 2), ("dragon", 5), ("orc", 1), ("goblin", 2), ("troll", 1),
                  ("ogre", 1), ("golem", 2), ("alien", 3), ("ufo", 1), ("monster", 2), ("slime", 2), ("devil", 1), ("imp", 1), ("kraken", 1), ("tentacle", 2),
                  ("minotaur", 1), ("werewolf", 1), ("mummy", 1), ("skeleton", 2), ("beast", 2), ("fangs", 1), ("claw", 3), ("horn", 1), ("tombstone", 1), ("coffin", 1)]),
    ("Heroes", [("wizard", 2), ("witch", 1), ("knight", 3), ("archer", 1), ("ninja", 3), ("pirate", 3), ("viking", 2), ("robot", 4), ("barbarian", 1),
                ("samurai", 1), ("king", 1), ("queen", 1), ("princess", 1), ("fairy", 1), ("elf", 2), ("dwarf", 1), ("mage", 1), ("warrior", 2),
                ("soldier", 2), ("astronaut", 1), ("cowboy", 1), ("pilot", 1), ("jester", 1), ("assassin", 1), ("cleric", 1), ("thief", 1), ("hood", 1)]),
    ("Items", [("key", 5), ("lantern", 3), ("torch", 2), ("candle", 2), ("hourglass", 2), ("compass", 1), ("map", 2), ("bell", 1), ("magnet", 1), ("gear", 3),
               ("wrench", 1), ("pickaxe", 1), ("shovel", 1), ("backpack", 1), ("barrel", 1), ("crate", 1), ("ladder", 1), ("door", 4), ("trap", 3), ("net", 1),
               ("rope", 2), ("anchor", 1), ("apple", 2), ("carrot", 1), ("meat", 2), ("bread", 1), ("cheese", 1), ("cake", 1), ("cup", 1),
               ("fruit", 2), ("mushroom", 2), ("heart", 6), ("clover", 1), ("star", 5), ("flag", 3), ("feather", 1), ("fist", 2), ("muscle", 1), ("eye", 3),
               ("brain", 1), ("footprint", 1), ("wing", 2), ("lever", 1), ("lock", 2), ("padlock", 1),
               ("pill", 1), ("syringe", 1), ("medkit", 1), ("bandage", 1), ("health", 2), ("energy", 2), ("fuel", 1), ("radar", 1), ("target", 3), ("sniper", 1)]),
    ("Vehicles", [("tank", 3), ("car", 2), ("truck", 2), ("plane", 2), ("helicopter", 2), ("ship", 4), ("sailboat", 2), ("submarine", 2), ("spaceship", 2),
                  ("motorcycle", 1), ("bicycle", 1), ("rocket", 2), ("jeep", 1), ("train", 2), ("bus", 1), ("boat", 2), ("fighter", 1), ("shuttle", 1), ("satellite", 1)]),
    ("Elements", [("flame", 4), ("fire", 5), ("ice", 4), ("snowflake", 2), ("drop", 3), ("leaf", 3), ("water", 3), ("wind", 2), ("tornado", 1), ("earth", 1),
                  ("rock", 2), ("thunder", 1), ("storm", 2), ("sun", 3), ("moon", 3), ("poison", 2), ("acid", 1), ("smoke", 1), ("lava", 1), ("volcano", 1),
                  ("rainbow", 1), ("sparks", 1), ("spark", 1), ("bolt", 2), ("wave", 1), ("snow", 1)]),
    ("Board & Cards", [("dice", 5), ("chess", 6), ("pawn", 1), ("puzzle", 1), ("joystick", 1), ("domino", 1), ("poker", 1), ("tarot", 1), ("cards", 1),
                       ("pac", 1), ("tetris", 1), ("pong", 1), ("slot", 1), ("roulette", 1), ("lotus", 0)]),
    ("Buildings", [("castle", 3), ("tower", 4), ("tent", 2), ("campfire", 1), ("bridge", 2), ("house", 2), ("fence", 1), ("gate", 2), ("temple", 1),
                   ("pyramid", 1), ("windmill", 1), ("barracks", 1), ("fort", 1), ("fortress", 1), ("church", 1), ("hut", 1), ("village", 1), ("cave", 1), ("well", 1), ("totem", 1)]),
    ("Wildlife", [("wolf", 2), ("bear", 2), ("cat", 2), ("dog", 2), ("horse", 2), ("fish", 3), ("bird", 3), ("snake", 3), ("spider", 3), ("bat", 3), ("owl", 1),
                  ("eagle", 1), ("lion", 1), ("tiger", 1), ("fox", 1), ("rabbit", 1), ("deer", 1), ("boar", 1), ("rat", 1), ("turtle", 1), ("crab", 1),
                  ("shark", 1), ("whale", 1), ("scorpion", 1), ("bee", 1), ("butterfly", 1), ("frog", 1), ("chicken", 1), ("cow", 1), ("pig", 1),
                  ("sheep", 1), ("elephant", 1), ("monkey", 1), ("penguin", 1), ("dinosaur", 1), ("trex", 1), ("tree", 4), ("palm", 1), ("pine", 1), ("oak", 1), ("flower", 2), ("cactus", 1), ("bug", 2), ("ant", 1)]),
]
GAME_EXPLICIT = """broadsword crossbow gauntlet breastplate boots coins gems cut-diamond crown trophy skull ghost zombie vampire-cape dragon-head
chest open-chest locked-chest health-potion magic-potion potion-ball spell-book open-book tied-scroll scroll-unfurled wizard-face witch-face
barbute pointy-hat ninja-mask pirate-hat joystick ufo spaceship rocket hourglass bomb ticket heart-plus half-heart heart-bottle heart-organ
wolf-head bear-head horse-head cat anchor skeleton-key boss-key key plain-dagger war-axe thor-hammer spiked-mace bone-mace shield""".split()
GAME_BAD = set("""bag tie pepper twitter usa brazil hot balloon grease sydney rialto pisa japanese tv radio golf hospital sports sport pocket
pope arabic asian disco jazz saxophone guitar violin piano drum tuba trumpet microphone cd vinyl cassette tape telephone phone laptop
computer camera toilet bathtub shower towel soap tooth toothbrush razor lipstick perfume dress shirt pants sock sandal slipper bikini
beer wine whisky vodka cocktail martini cigarette smoking lighter ashtray pipe cannabis marijuana syringe-gun gambling casino
usb sewing slot fork id bill cutter saw plug mill ping upload download play next cog dust sniper-scope meeple baby diaper pacifier bottle-cap stroller toy teddy bear-trap lingerie underwear erotic nude breast butt bikini sexy""".split())
GAME_MAX_PATH = 2000
GAME_BAD_NAMES = {"car-key", "car-door", "car-seat", "hand-truck", "ammo-box", "arm-bandage", "ice-pop", "coffee-cup", "stairs-cake", "kiwi-fruit", "kiwi-bird", "dog-bowl", "fish-bucket", "bird-cage", "flower-pot", "chicken-leg", "pill-drop", "bolt-drop", "water-gun", "earth-spit", "moon-bats", "sun-priest", "evil-moon", "tentacle-heart", "gun-stock", "dice-shield", "cloud-ring", "pay-money", "pearl-earring"}

GAME_SYN = {
    "sword": "weapon blade fight attack battle melee steel",
    "broadsword": "sword weapon blade knight medieval",
    "dagger": "knife weapon blade stab rogue assassin thief",
    "axe": "weapon chop wood battle viking",
    "spear": "weapon lance javelin throw",
    "mace": "weapon club hammer blunt",
    "hammer": "weapon tool smash build forge",
    "bow": "weapon archer shoot ranged arrow",
    "crossbow": "weapon bolt shoot ranged archer",
    "arrow": "projectile shoot ammo bow direction",
    "bomb": "explosive explode boom danger tnt fuse",
    "grenade": "explosive throw frag boom",
    "dynamite": "tnt explosive boom",
    "pistol": "gun weapon shoot handgun",
    "rifle": "gun weapon shoot sniper",
    "gun": "weapon shoot firearm",
    "cannon": "weapon artillery ship fire pirate",
    "shuriken": "ninja star throw weapon",
    "boomerang": "throw weapon return",
    "missile": "rocket projectile weapon explosive",
    "rocket": "missile launch space spaceship boost",
    "shield": "defense defence protect armor guard block safe",
    "helmet": "armor head protect knight soldier",
    "armor": "protect defense knight chest vest",
    "breastplate": "armor chest protect knight",
    "gauntlet": "armor glove hand knight",
    "boots": "armor shoes feet speed",
    "cape": "cloak hero vampire",
    "crown": "king queen royal leader vip rank premium",
    "coin": "money gold cash currency token reward",
    "coins": "money gold cash currency treasure",
    "gem": "jewel crystal diamond treasure premium currency",
    "gems": "jewels crystals diamonds treasure premium currency",
    "diamond": "gem jewel crystal treasure premium",
    "crystal": "gem magic mana shard",
    "chest": "treasure loot box reward container",
    "treasure": "loot gold reward chest",
    "trophy": "win winner victory cup prize award champion",
    "medal": "win award achievement rank badge",
    "ring": "jewel magic power",
    "gold": "money coin treasure rich",
    "potion": "magic health mana heal drink elixir bottle",
    "flask": "potion chemistry lab bottle liquid",
    "wand": "magic spell wizard staff",
    "staff": "magic wizard weapon rod",
    "orb": "magic sphere crystal power ball",
    "scroll": "spell quest paper letter message map",
    "book": "spell lore story library read magic",
    "rune": "magic symbol stone ancient",
    "portal": "teleport gate magic door warp",
    "lightning": "bolt electric power energy storm spell",
    "skull": "death dead die danger enemy poison kill game over bones",
    "bone": "skeleton death",
    "ghost": "spirit enemy halloween spooky undead",
    "zombie": "undead enemy walker monster",
    "vampire": "undead enemy blood dracula",
    "demon": "devil evil monster enemy",
    "dragon": "monster fire fantasy boss beast",
    "orc": "monster enemy warrior green",
    "goblin": "monster enemy green",
    "troll": "monster enemy giant",
    "golem": "monster enemy stone giant",
    "alien": "space monster enemy ufo",
    "ufo": "alien space ship saucer",
    "monster": "enemy creature beast boss",
    "wizard": "mage magic spell hero",
    "witch": "magic spell hag hero",
    "knight": "hero warrior armor medieval",
    "archer": "hero ranger bow",
    "ninja": "hero stealth assassin",
    "pirate": "hero ship sea captain",
    "viking": "hero warrior norse",
    "robot": "bot machine enemy hero ai",
    "key": "unlock door access chest open",
    "lantern": "light lamp fire",
    "torch": "light fire flame dungeon",
    "candle": "light fire flame",
    "hourglass": "time wait timer sand",
    "compass": "direction navigate north explore",
    "map": "world level navigate explore treasure",
    "bell": "alarm ring notification",
    "gear": "settings cog machine",
    "door": "exit enter room gate",
    "trap": "danger spikes snare",
    "heart": "love like life lives health hp",
    "star": "favorite rating score bonus level rank",
    "flag": "goal checkpoint banner mark",
    "target": "aim goal bullseye crosshair",
    "tank": "vehicle war military armor",
    "car": "vehicle drive race",
    "plane": "aircraft fly vehicle",
    "ship": "boat vessel sea sail",
    "spaceship": "space ship rocket vehicle shooter",
    "flame": "fire burn hot",
    "fire": "flame burn hot attack",
    "ice": "frost cold freeze winter",
    "snowflake": "ice frost cold winter",
    "drop": "water liquid rain blood",
    "leaf": "plant nature green eco",
    "tree": "forest plant nature wood",
    "dice": "random chance luck roll gamble board",
    "chess": "board strategy piece",
    "puzzle": "piece jigsaw solve",
    "joystick": "controller arcade stick gamepad",
    "castle": "fortress kingdom building tower base",
    "tower": "building defense base castle",
    "tent": "camp outdoor",
    "campfire": "fire camp rest",
    "wolf": "animal beast",
    "snake": "animal reptile",
    "spider": "animal enemy web",
    "bat": "animal enemy night",
    "fish": "animal sea water",
    "bird": "animal fly",
    "eye": "see look vision",
    "fist": "punch hand fight attack power",
    "muscle": "strong power arm",
    "wing": "fly angel bird",
    "feather": "bird light",
    "apple": "fruit food health",
    "meat": "food ham steak",
    "mushroom": "plant forest",
}


def game_category_lookup():
    out = {}
    for cat, items in GAME_SPEC:
        for kw, n in items:
            out.setdefault(kw, (cat, n))
    return out


def build_game_icons(gi_dir, taken_ids):
    """Returns the list of catalog entries for the game set. gi_dir = unpacked @iconify-json/game-icons."""
    data = json.load(open(os.path.join(gi_dir, "icons.json"), encoding="utf-8"))
    src = data["icons"]
    width = int(data.get("width", 512))

    def d_of(name):
        m = re.search(r'\sd="([^"]+)"', src[name]["body"])
        return m.group(1) if m else None

    def ok(name):
        words_ = name.split("-")
        if set(words_) & GAME_BAD or name in GAME_BAD_NAMES:
            return False
        dd = d_of(name)
        return bool(dd) and len(dd) <= GAME_MAX_PATH

    chosen = []
    seen = set()

    def take(name, cat):
        if name in src and name not in seen and (ok(name) or (name in GAME_EXPLICIT and len(d_of(name) or "") <= 3800 and name not in GAME_BAD_NAMES)):
            seen.add(name)
            chosen.append((name, cat))
            return True
        return False

    for cat, items in GAME_SPEC:
        for kw, n in items:
            if n <= 0:
                continue
            cands = [nm for nm in src if kw in nm.split("-") and ok(nm) and nm not in seen]
            # basic first: short name, then short path
            cands.sort(key=lambda nm: (len(nm.split("-")), len(nm), len(d_of(nm)), nm))
            got = 0
            for nm in cands:
                if got >= n:
                    break
                if take(nm, cat):
                    got += 1
    for nm in GAME_EXPLICIT:
        cat = "Items"
        for c, items in GAME_SPEC:
            if any(kw in nm.split("-") for kw, _ in items):
                cat = c
                break
        take(nm, cat)

    entries = []
    for name, cat in chosen:
        i = name if name not in taken_ids else name + "-game"
        ws = words(name)
        tags = []
        seen_t = set(words(i))

        def add(lst):
            for w in lst:
                w = w.lower()
                if w and w not in seen_t and len(w) > 1:
                    seen_t.add(w)
                    tags.append(w)

        for w in ws:
            if w in GAME_SYN:
                add(GAME_SYN[w].split())
            if w in SYN_TOKEN:
                add(SYN_TOKEN[w].split())
        add(["game"])
        entries.append({"i": i, "c": CI[cat], "s": 1, "w": width, "t": " ".join(tags), "p": minify(d_of(name))})
    return entries


def fetch_game_icons():
    tmp = tempfile.mkdtemp(prefix="gameicons_")
    subprocess.check_call(["npm", "pack", "@iconify-json/game-icons"], cwd=tmp, stdout=subprocess.DEVNULL)
    tgz = [f for f in os.listdir(tmp) if f.endswith(".tgz")][0]
    subprocess.check_call(["tar", "xzf", tgz], cwd=tmp)
    return os.path.join(tmp, "package")

# --------------------------------------------------------------------------------------------- SVG path tokenizer/minifier
_NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def tokenize(d):
    """Yield (command, [numbers]) segments. Handles arc flags written without separators. Raises ValueError on bad data."""
    i, n = 0, len(d)
    out = []
    cmd = None
    while i < n:
        ch = d[i]
        if ch in " ,\t\r\n":
            i += 1
            continue
        if ch.isalpha() and ch.upper() in _ARGS:
            cmd = ch
            i += 1
            if ch.upper() == "Z":
                out.append((cmd, []))
            continue
        if cmd is None:
            raise ValueError("path data does not start with a command")
        need = _ARGS[cmd.upper()]
        if need == 0:
            raise ValueError("numbers after Z")
        nums = []
        for k in range(need):
            while i < n and d[i] in " ,\t\r\n":
                i += 1
            if cmd.upper() == "A" and k in (3, 4):
                if i >= n or d[i] not in "01":
                    raise ValueError("bad arc flag")
                nums.append(float(d[i]))
                i += 1
                continue
            m = _NUM.match(d, i)
            if not m:
                raise ValueError("bad number at %d: %r" % (i, d[i:i + 12]))
            nums.append(float(m.group()))
            i += m.end() - i
        out.append((cmd, nums))
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return out


def fmt(v, nd=2):
    s = ("%." + str(nd) + "f") % v
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    if s.startswith("0.") and len(s) > 1:
        s = s[1:]
    elif s.startswith("-0."):
        s = "-" + s[2:]
    return s


def minify(d):
    """Re-emit the path with numbers rounded to 2 decimals and no needless separators (geometry changes by < 0.005 units)."""
    parts = []
    for cmd, nums in tokenize(d):
        if cmd.upper() == "A":
            body = " ".join([fmt(nums[0]), fmt(nums[1]), fmt(nums[2]), str(int(nums[3])), str(int(nums[4])), fmt(nums[5]), fmt(nums[6])])
        else:
            body = ""
            for v in nums:
                t = fmt(v)
                if body and not t.startswith("-"):
                    body += " "
                body += t
        parts.append(cmd + body)
    return "".join(parts)


# --------------------------------------------------------------------------------------------- build
def words(name):
    return [w for w in re.split(r"[^a-z0-9]+", name.lower()) if w]


def build_tags(name, aliases, mdi_cats):
    own = set(words(name))
    tags = []
    seen = set(own)

    def add(ws):
        for w in ws:
            w = w.lower()
            if w and w not in seen and len(w) > 1 and w not in ALIAS_STOP:
                seen.add(w)
                tags.append(w)

    for a in aliases:
        add(words(a))
    for w in words(name):
        if w in SYN_TOKEN:
            add(SYN_TOKEN[w].split())
    if name in SYN_NAME:
        add(SYN_NAME[name].split())
    # MDI category words are weak hints; only game/UI-relevant ones (not "home automation", "files", "date"...)
    for c in mdi_cats[:3]:
        w = words(c)[:1]
        if w and w[0] in CAT_HINTS:
            add(w)
    return " ".join(tags)


def category_for(name, explicit_cat, mdi_cats):
    if explicit_cat:
        return explicit_cat
    for c in mdi_cats:
        if c in BULK:
            return BULK[c]
    return "Objects"


# Keep the catalog lean (the user wants lots of icons but a small APK): drop families that are rarely useful in a game UI,
# and every "-outline" twin except the core ones below.
DROP_RE = re.compile(r"^(alpha-|language-|file-|format-|clock-time-|numeric-\d-box|numeric-\d-circle-outline|numeric-negative|numeric-positive|"
                     r"battery-\d+|cloud-|window-|signal-cellular|wifi-strength|calendar-|account-(?!circle$|group$|multiple$|plus$|minus$|star$|heart$|key$|lock$|check$|remove$|alert$|search$|box$|tie$)|"
                     r"face-|human-(?!male$|female$|child$|greeting$|handsup$|handsdown$)|hand-(?!pointing|peace|okay|clap|wave|coin|heart|extended)|"
                     r"timer-(?!sand$|outline$|off$|play$|pause$|stop$)|bag-|bike-|car-(?!side$|sports$|electric$)|truck-|van-|hospital-|bell-(?!outline$|ring$|off$|alert$|badge$)|"
                     r"alarm-(?!check$|plus$|off$)|speaker-|surround-|microphone-|music-note-(?!off$|plus$|eighth$|half$|quarter$|whole$)|music-accidental|music-rest|"
                     r"gesture-|cursor-text|flag-triangle|layers-|image-(?!outline$|multiple$)|pencil-|puzzle-|cards-playing-|gamepad-(?!variant|square|round$)|"
                     r"folder-|content-|clipboard-|progress-|source-|code-|text-|tag-(?!outline$|heart$|plus$|multiple$)|ticket-|calendar|"
                     r"weather-(?!sunny$|night$|cloudy$|rainy$|pouring$|snowy$|lightning$|windy$|fog$|partly-cloudy$|sunset$)|moon-|vector-|border-|dots-|crop-|"
                     r"alert-(?!circle$|outline$|octagon$|decagram$|box$)|check-(?!bold$|circle$|all$|decagram$)|close-(?!thick$|circle$|box$|octagon$)|"
                     r"arrow-(?!left|right|up|down|top|bottom|projectile|decision|expand|collapse|split|u-)|chevron-(?!left$|right$|up$|down$|double)|"
                     r"coin-outline|cash-(?!multiple$|plus$|minus$)|currency-(?!usd$|eur$|gbp$|jpy$|btc$|sign$|inr$|try$)|sack-|safe|receipt|cart-(?!outline$|plus$|minus$|check$|heart$|remove$)|"
                     r"credit-card-|wallet-|basket-|store-|gift-(?!outline$|open$)|food-(?!apple$|drumstick$|steak$|variant$)|fruit-|bottle-(?!tonic|soda|wine|water)|"
                     r"glass-|silverware-|bowl-|pot-|coffee-|tea-|cake-|cup-|beer-)")
CORE_OUTLINE = set("""heart star home bell lock lock-open eye circle square triangle hexagon pentagon octagon check-circle close-circle plus-circle
minus-circle play-circle pause-circle stop-circle information help-circle alert-circle alert thumb-up thumb-down trophy flag
bookmark diamond shield crown skull ghost robot email chat comment clock timer calendar camera image account-circle account
cart gift lightbulb lightbulb-on music-note rocket cat dog cloud tree flower leaf delete pencil emoticon-happy emoticon-sad
emoticon-neutral emoticon-cool emoticon-angry emoticon-cry emoticon-wink emoticon-excited emoticon""".split())
MAX_PATH = 1100


def skip_name(name):
    if name in MUST_SET:
        return False
    if name.endswith("-outline") and name[:-8] not in CORE_OUTLINE:
        return True
    return bool(DROP_RE.match(name))


def load_mdi(mdi):
    meta = json.load(open(os.path.join(mdi, "meta.json"), encoding="utf-8"))
    by = {}
    for it in meta:
        if it.get("deprecated"):
            continue
        by[it["name"]] = it
    return by


def svg_path(mdi, name):
    s = open(os.path.join(mdi, "svg", name + ".svg"), encoding="utf-8").read()
    ps = re.findall(r'<path[^>]*\sd="([^"]+)"', s)
    if len(ps) != 1:
        raise ValueError("%s: expected one path, got %d" % (name, len(ps)))
    return ps[0]


def fetch_mdi():
    tmp = tempfile.mkdtemp(prefix="mdi_")
    subprocess.check_call(["npm", "pack", "@mdi/svg"], cwd=tmp, stdout=subprocess.DEVNULL)
    tgz = [f for f in os.listdir(tmp) if f.endswith(".tgz")][0]
    subprocess.check_call(["tar", "xzf", tgz], cwd=tmp)
    return os.path.join(tmp, "package")


# the must-haves from the task (every one has to exist in the final catalog)
MUST = """heart star volume-high volume-off volume-low check close music-note music-off pause stop play exit-run exit-to-app logout
home cog lock lock-open coin gem trophy store cart sword shield gamepad gamepad-variant timer clock arrow-left arrow-right
arrow-up arrow-down chevron-left chevron-right skip-next skip-previous fast-forward rewind refresh restart undo redo menu plus minus
help-circle information alert eye eye-off magnify delete pencil content-save download upload camera image rocket ghost fire
lightning-bolt key flag bomb skull crown account account-group bell gift medal map-marker crosshairs target bullseye
thumb-up thumb-down emoticon-happy emoticon-sad bow-arrow axe magic-staff wizard-hat treasure-chest potion dice-6 puzzle
fullscreen fullscreen-exit share-variant link wifi battery bluetooth leaf flower paw robot heart-broken heart-half-full
star-half shield-half-full joystick dpad coin-stack tree cat dog fish bird snowflake weather-sunny weather-night
moon-waning-crescent emoticon-cry emoticon-angry ladder castle tank pistol hammer wrench shopping-outline""".split()


MUST_SET = set(MUST)
GAME_MUST = """sword shield coins gem potion key chest crown trophy bomb bow helmet wand scroll skull dragon heart star ghost robot spaceship tank
dagger axe crossbow flask book lantern torch castle tower wolf spider snake fish dice chess flame ice lightning leaf tree""".split()


def main(argv):
    check_only = "--check" in argv
    mdi = None
    gi = None
    if "--mdi" in argv:
        mdi = argv[argv.index("--mdi") + 1]
    if "--game-icons" in argv:
        gi = argv[argv.index("--game-icons") + 1]
    if check_only:
        return check()
    if mdi is None:
        mdi = fetch_mdi()
    if gi is None:
        gi = fetch_game_icons()
    by = load_mdi(mdi)

    chosen = {}      # name -> app category (explicit)
    order = []

    def take(name, cat):
        if name in by and name not in chosen:
            chosen[name] = cat
            order.append(name)
            return True
        return False

    missing_explicit = []
    for cat, blob in EXPLICIT.items():
        for nm in blob.split():
            if skip_name(nm):
                continue
            if not take(nm, cat) and nm not in by:
                missing_explicit.append(nm)

    # bulk categories (base names only), capped per category
    taken_bulk = {}
    for name in sorted(by):
        it = by[name]
        if name in chosen:
            continue
        for c in it["tags"]:
            if c in BULK and BULK_LIMIT.get(c, 0) > 0:
                if set(name.split("-")) & BULK_BAD or skip_name(name):
                    continue
                if taken_bulk.get(c, 0) >= BULK_LIMIT[c]:
                    continue
                taken_bulk[c] = taken_bulk.get(c, 0) + 1
                take(name, BULK[c])
                break

    icons = []
    for name in order:
        it = by[name]
        d = minify(svg_path(mdi, name))
        if len(d) > MAX_PATH and name not in MUST_SET:
            continue
        icons.append({"i": name, "c": CI[chosen[name]], "t": build_tags(name, it.get("aliases", []), it.get("tags", [])), "p": d})
    for ex in EXTRAS:
        if ex["i"] in chosen:
            continue
        tags = ex["t"].split()
        own = set(words(ex["i"]))
        tags = [t for t in dict.fromkeys(tags) if t not in own]
        icons.append({"i": ex["i"], "c": CI[ex["c"]], "t": " ".join(tags), "p": minify(ex["p"])})
        chosen[ex["i"]] = ex["c"]

    game = build_game_icons(gi, {i["i"] for i in icons})
    icons.extend(game)
    # aliases that people will type but MDI does not carry (gem -> diamond-stone, coin -> ... ) are covered by SYN_NAME/SYN_TOKEN
    have = {i["i"] for i in icons}
    missing_must = [m for m in MUST if m not in have]

    os.makedirs(OUT_DIR, exist_ok=True)
    doc = {"v": 2, "source": "Material Design Icons (Apache-2.0) + game-icons.net (CC BY 3.0) + a few hand-made extras",
           "sets": SETS, "categories": CATEGORIES, "icons": icons}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    write_license(mdi)
    write_game_license(gi)
    size = os.path.getsize(OUT)
    per = {}
    for i in icons:
        per[CATEGORIES[i["c"]]] = per.get(CATEGORIES[i["c"]], 0) + 1
    print("icons:", len(icons), "(game set: %d)" % len(game), "bytes:", size)
    print("per category:", per)
    print("explicit names not in MDI (skipped):", sorted(set(missing_explicit)))
    print("MISSING must-haves:", missing_must)
    print("game per category:", {CATEGORIES[k]: sum(1 for g in game if g["c"] == k) for k in sorted({g["c"] for g in game})})
    return check()


def write_license(mdi):
    parts = ["Star2D built-in icon library\n"
             "============================\n\n"
             "Most icons are from Material Design Icons by the Pictogrammers group (https://pictogrammers.com/library/mdi/,\n"
             "https://github.com/Templarian/MaterialDesign), used under the Apache License 2.0. A few extra game icons\n"
             "(coin, coin-stack, gem, joystick, potion, ...) were drawn for Star2D. The icons are changed only by rounding\n"
             "path coordinates to two decimals.\n\n"
             "---- Pictogrammers Free License (LICENSE of @mdi/svg) ----\n\n"]
    lic = os.path.join(mdi, "LICENSE")
    parts.append(open(lic, encoding="utf-8").read() if os.path.exists(lic) else "")
    parts.append("\n\n---- Apache License 2.0 ----\n\n")
    apache = "/usr/share/common-licenses/Apache-2.0"
    parts.append(open(apache, encoding="utf-8").read() if os.path.exists(apache) else "See https://www.apache.org/licenses/LICENSE-2.0\n")
    with open(os.path.join(OUT_DIR, "LICENSE-MDI.txt"), "w", encoding="utf-8") as f:
        f.write("".join(parts))


def write_game_license(gi):
    info = {}
    try:
        info = json.load(open(os.path.join(gi, "info.json"), encoding="utf-8"))
    except Exception:
        pass
    author = info.get("author", {})
    lic = info.get("license", {})
    text = (
        "Game Icons (game-icons.net)\n"
        "===========================\n\n"
        "The icons of the 'Game' set (swords, shields, potions, monsters, ...) come from Game Icons, https://game-icons.net,\n"
        "icons by Lorc, Delapouite & contributors, licensed under Creative Commons Attribution 3.0 (CC BY 3.0).\n"
        "Source: https://github.com/game-icons/icons\n"
        "License: https://creativecommons.org/licenses/by/3.0/\n\n"
        "Packaging information (info.json of the npm package @iconify-json/game-icons %s, copied exactly):\n"
        "  name:    %s\n"
        "  author:  %s (%s)\n"
        "  license: %s (%s) %s\n\n"
        "Changes made by Star2D (adaptation notice required by CC BY 3.0): only a selection of the icons is included; the SVG path\n"
        "data is kept, with numbers rounded to two decimals; the shapes are drawn as plain single-colour PNG images (white by default,\n"
        "or tinted with the colour chosen when importing), optionally on a coloured badge or with a shadow.\n\n"
        "You may use these icons in your games, including commercial ones, as long as you give credit, for example in the credits\n"
        "screen: 'Icons by Lorc, Delapouite & contributors from game-icons.net, CC BY 3.0'.\n"
    ) % (
        "1.2.x",
        info.get("name", "Game Icons"),
        author.get("name", "GameIcons"), author.get("url", "https://github.com/game-icons/icons"),
        lic.get("title", "CC BY 3.0"), lic.get("spdx", "CC-BY-3.0"), lic.get("url", ""),
    )
    with open(os.path.join(OUT_DIR, "LICENSE-GAME-ICONS.txt"), "w", encoding="utf-8") as f:
        f.write(text)


def check():
    ok = True
    if not os.path.exists(OUT):
        print("catalog.json is missing")
        return 1
    size = os.path.getsize(OUT)
    doc = json.load(open(OUT, encoding="utf-8"))
    icons = doc["icons"]
    ids = [i["i"] for i in icons]
    dup = {x for x in ids if ids.count(x) > 1}
    if dup:
        print("duplicate ids:", dup)
        ok = False
    have = set(ids)
    miss = [m for m in MUST if m not in have]
    if miss:
        print("missing must-haves:", miss)
        ok = False
    bad = []
    for i in icons:
        try:
            segs = tokenize(i["p"])
            if not segs or segs[0][0] not in "Mm":
                bad.append(i["i"])
        except ValueError as e:
            bad.append("%s (%s)" % (i["i"], e))
        if not (0 <= i["c"] < len(doc["categories"])):
            bad.append(i["i"] + " (category)")
        if not re.fullmatch(r"[a-z0-9\-]+", i["i"]):
            bad.append(i["i"] + " (id)")
    if bad:
        print("bad icons:", bad[:20])
        ok = False
    game_ids = {i["i"] for i in icons if i.get("s") == 1}
    gmiss = [m for m in GAME_MUST if not any(g == m or g.startswith(m + "-") or g.endswith("-" + m) or ("-" + m + "-") in g for g in game_ids)]
    if gmiss:
        print("game set lacks:", gmiss)
        ok = False
    for i in icons:
        if i.get("s", 0) not in (0, 1) or i.get("w", 24) not in (24, 512):
            print("bad set/viewBox:", i["i"])
            ok = False
    if size > MAX_BYTES:
        print("catalog too large: %d bytes (max %d)" % (size, MAX_BYTES))
        ok = False
    # no duplicate paths needed, but flag heavy ones
    heavy = sorted(icons, key=lambda x: -len(x["p"]))[:3]
    print("check:", "OK" if ok else "FAILED", "| icons", len(icons), "| bytes", size, "| heaviest", [(h["i"], len(h["p"])) for h in heavy])
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
