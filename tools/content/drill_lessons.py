import json
def L(title_en, title_fr, points, example=None, fifths=0, tip=None):
    d = {"title": {"en": title_en, "fr": title_fr}, "points": [{"en": e, "fr": f} for e, f in points]}
    if example: d["example"] = example; d["fifths"] = fifths
    if tip: d["tip"] = {"en": tip[0], "fr": tip[1]}
    return d

READING = [
 L("The staff and the treble clef", "La portée et la clé de sol", [
  ("Music is written on five lines. Notes sit either on a line or in a space between two lines.", "La musique s'écrit sur cinq lignes. Une note est soit sur une ligne, soit dans un interligne."),
  ("The treble clef curls around the second line from the bottom: that line is {G}. The saxophone always reads in treble clef.", "La clé de sol s'enroule autour de la deuxième ligne en partant du bas : cette ligne est {G}. Le saxophone lit toujours en clé de sol."),
  ("Lines, from bottom to top: {E} {G} {B} {D} {F}. Spaces: {F} {A} {C} {E}.", "Lignes, de bas en haut : {E} {G} {B} {D} {F}. Interlignes : {F} {A} {C} {E}."),
  ("Each step up (line → space → line) is the next letter. Going down, go back one letter.", "Chaque marche vers le haut (ligne → interligne → ligne) donne la note suivante. Vers le bas, la note précédente."),
 ], ["E4:1/4 F4:1/4 G4:1/4 A4:1/4", "B4:1/4 C5:1/4 D5:1/4 E5:1/4", "F5:1/1"], tip=("Learn the spaces first: from the bottom they spell F-A-C-E. Then find any line from its neighbours.", "Apprenez d'abord les interlignes, puis trouvez chaque ligne grâce à ses voisins.")),
 L("Ledger lines", "Les lignes supplémentaires", [
  ("Notes above or below the staff get short extra lines called ledger lines. They work like the staff continued.", "Au-dessus ou au-dessous de la portée, on ajoute de petites lignes : les lignes supplémentaires. Elles prolongent la portée."),
  ("Below: {D} hangs under the bottom line, {C} sits on the first ledger line.", "En dessous : {D} est sous la première ligne, {C} est sur la première ligne supplémentaire."),
  ("Above: {G} sits on top of the staff, {A} on the first ledger line above.", "Au-dessus : {G} est posé sur la portée, {A} est sur la première ligne supplémentaire au-dessus."),
 ], ["C4:1/4 D4:1/4 E4:1/2", "G5:1/4 A5:1/4 G5:1/2"], tip=("Count lines from the staff outward, saying the letters: E, D, C going down; F, G, A going up.", "Comptez depuis la portée vers l'extérieur en disant les notes : mi, ré, do en descendant ; fa, sol, la en montant.")),
 L("The whole range of the alto", "Toute la tessiture de l'alto", [
  ("The alto saxophone is written from low {Bb} (below the staff, two ledger lines and more) to high {F#} (three ledger lines above).", "Le saxophone alto s'écrit du {Bb} grave (sous la portée) au {F#} aigu (trois lignes supplémentaires au-dessus)."),
  ("Low {B} and {Bb} sit just under the ledger line of {C}. High {B} and {C} sit on the second ledger line and above it.", "{B} et {Bb} graves sont juste sous la ligne supplémentaire du {C}. Le {B} et le {C} aigus sont sur et au-dessus de la deuxième ligne supplémentaire."),
  ("Notes with many ledger lines are read by landmarks: middle {C} (one line below), high {A} (one line above), high {C} (two lines above).", "Les notes à plusieurs lignes se lisent avec des repères : {C} grave (une ligne en dessous), {A} aigu (une ligne au-dessus), {C} suraigu (deux lignes au-dessus)."),
 ], ["Bb3:1/4 B3:1/4 C4:1/2", "A5:1/4 B5:1/4 C6:1/4 D6:1/4"], tip=("Find the nearest landmark, then count steps from it.", "Trouvez le repère le plus proche, puis comptez les marches à partir de lui.")),
 L("Reading in a key signature", "Lire avec une armure", [
  ("The sharps or flats at the start of the line are the key signature. They apply to every note of that letter, in every octave, for the whole piece.", "Les dièses ou bémols au début de la ligne forment l'armure. Ils s'appliquent à toutes les notes de ce nom, à toutes les octaves, pendant tout le morceau."),
  ("So a note can be sharp or flat with no sign next to it: with one flat ({Bb}) in the key, a note on the middle line is {Bb}, not {B}.", "Une note peut donc être diésée ou bémolisée sans signe à côté : avec un bémol ({Bb}) à l'armure, la note sur la ligne du milieu est {Bb}, pas {B}."),
  ("Method: before reading, say the key's altered notes out loud (\"{F#} {C#}\"). Then name each note by its place, and add the key's sharp or flat if its letter is in the list.", "Méthode : avant de lire, dites les notes altérées de l'armure (« {F#} {C#} »). Puis nommez chaque note par sa place, et ajoutez le dièse ou le bémol si son nom est dans la liste."),
  ("Sharps always come in the order {F#} {C#} {G#}; flats in the order {Bb} {Eb} {Ab}.", "Les dièses viennent toujours dans l'ordre {F#} {C#} {G#} ; les bémols dans l'ordre {Bb} {Eb} {Ab}."),
 ], ["F4:1/4 B4:1/4 E5:1/4 B4:1/4", "A4:1/4 E4:1/4 B4:1/2"], fifths=-2, tip=("Here (2 flats) every {B} and every {E} is flat, at any height.", "Ici (2 bémols) chaque {B} et chaque {E} est bémol, à toutes les hauteurs.")),
 L("More keys, and accidentals in the bar", "Plus d'armures, et altérations dans la mesure", [
  ("Keys now go up to 5 sharps ({F#} {C#} {G#} {D#} {A#}) or 5 flats ({Bb} {Eb} {Ab} {Db} {Gb}), over the whole range.", "Les armures vont maintenant jusqu'à 5 dièses ({F#} {C#} {G#} {D#} {A#}) ou 5 bémols ({Bb} {Eb} {Ab} {Db} {Gb}), sur toute la tessiture."),
  ("An accidental written next to a note beats the key signature until the end of the bar. A natural ♮ cancels the key: in 2 sharps, a note on the bottom space with ♮ is {F}.", "Une altération écrite devant une note l'emporte sur l'armure jusqu'à la fin de la mesure. Un bécarre ♮ annule l'armure : avec 2 dièses, la note du premier interligne avec ♮ est {F}."),
  ("So the order of reading is: sign next to the note first; if none, the key signature; if the letter is not in the key, it is natural.", "L'ordre de lecture est donc : le signe devant la note d'abord ; sinon l'armure ; si la note n'est pas dans l'armure, elle est naturelle."),
 ], ["D5:1/4 Fn5:1/4 C#5:1/4 D5:1/4"], fifths=2, tip=("This is how real music is written: read the key before the first note, every time.", "C'est ainsi qu'est écrite la vraie musique : lisez l'armure avant la première note, à chaque fois.")),
]

SCALE = [
 L("Major scales and key signatures", "Gammes majeures et armures", [
  ("A major scale follows the pattern: whole, whole, half, whole, whole, whole, half (W W H W W W H).", "Une gamme majeure suit le schéma : ton, ton, demi-ton, ton, ton, ton, demi-ton."),
  ("The sharps or flats a scale needs are written once at the start of each line: the key signature.", "Les dièses ou bémols nécessaires sont écrits une fois au début de chaque ligne : l'armure."),
  ("No sharp or flat: {C} major. One sharp ({F#}): {G} major. One flat ({Bb}): {F} major.", "Sans altération : {C} majeur. Un dièse ({F#}) : {G} majeur. Un bémol ({Bb}) : {F} majeur."),
 ], ["G4:1/8 A4:1/8 B4:1/8 C5:1/8 D5:1/8 E5:1/8 F#5:1/8 G5:1/8"], fifths=1, tip=("Questions show a key signature, notes, or ask you to build a scale. Name the tonic: the first note of the scale.", "Les questions montrent une armure, des notes, ou demandent de construire une gamme. Nommez la tonique : la première note.")),
 L("Two sharps or flats", "Deux dièses ou bémols", [
  ("Sharps are added in the order {F#} {C#} {G#} {D#}…; flats in the order {Bb} {Eb} {Ab} {Db}….", "Les dièses s'ajoutent dans l'ordre {F#} {C#} {G#} {D#}… ; les bémols dans l'ordre {Bb} {Eb} {Ab} {Db}…."),
  ("Sharps: the major key is a half step above the last sharp. Two sharps ({F#} {C#}) → {D} major.", "Dièses : la tonalité majeure est un demi-ton au-dessus du dernier dièse. Deux dièses ({F#} {C#}) → {D} majeur."),
  ("Flats: the major key is the next-to-last flat. Two flats ({Bb} {Eb}) → {Bb} major.", "Bémols : la tonalité majeure est l'avant-dernier bémol. Deux bémols ({Bb} {Eb}) → {Bb} majeur."),
 ], ["D4:1/8 E4:1/8 F#4:1/8 G4:1/8 A4:1/8 B4:1/8 C#5:1/8 D5:1/8"], fifths=2),
 L("Up to four sharps or flats", "Jusqu'à quatre dièses ou bémols", [
  ("Three sharps → {A} major, four sharps → {E} major.", "Trois dièses → {A} majeur, quatre dièses → {E} majeur."),
  ("Three flats → {Eb} major, four flats → {Ab} major.", "Trois bémols → {Eb} majeur, quatre bémols → {Ab} majeur."),
  ("The two rules still work: half step above the last sharp; next-to-last flat.", "Les deux règles marchent toujours : un demi-ton au-dessus du dernier dièse ; l'avant-dernier bémol."),
 ], ["Eb4:1/8 F4:1/8 G4:1/8 Ab4:1/8 Bb4:1/8 C5:1/8 D5:1/8 Eb5:1/8"], fifths=-3),
 L("Minor scales: the relative minor", "Gammes mineures : la relative", [
  ("Each key signature serves a major key and a minor key: its relative minor, which starts on the 6th note of the major scale.", "Chaque armure sert une tonalité majeure et une mineure : sa relative, qui commence sur la 6e note de la gamme majeure."),
  ("Shortcut: the relative minor is a minor 3rd (3 half steps) below the major tonic. {C} major ↔ {A} minor, {G} major ↔ {E} minor, {F} major ↔ {D} minor.", "Raccourci : la relative mineure est une tierce mineure (3 demi-tons) sous la tonique majeure. {C} majeur ↔ {A} mineur, {G} majeur ↔ {E} mineur, {F} majeur ↔ {D} mineur."),
  ("Natural minor pattern: W H W W H W W.", "Schéma du mineur naturel : ton, demi-ton, ton, ton, demi-ton, ton, ton."),
 ], ["A4:1/8 B4:1/8 C5:1/8 D5:1/8 E5:1/8 F5:1/8 G5:1/8 A5:1/8"], tip=("When the notes start and end on the 6th degree of the key, it is the minor.", "Quand les notes commencent et finissent sur le 6e degré de l'armure, c'est la mineure.")),
 L("Major and minor, up to four", "Majeur et mineur, jusqu'à quatre", [
  ("Find the major key from the signature, then go down a minor 3rd for its minor.", "Trouvez la tonalité majeure grâce à l'armure, puis descendez d'une tierce mineure pour sa mineure."),
  ("Examples: 3 flats = {Eb} major / {C} minor. 4 sharps = {E} major / {C#} minor.", "Exemples : 3 bémols = {Eb} majeur / {C} mineur. 4 dièses = {E} majeur / {C#} mineur."),
 ], ["C5:1/8 D5:1/8 Eb5:1/8 F5:1/8 G5:1/8 Ab5:1/8 Bb5:1/8 C6:1/8"], fifths=-3),
 L("Every key", "Toutes les tonalités", [
  ("The circle of fifths: each sharp added moves the key up a 5th ({C} {G} {D} {A} {E} {B} {F#} {C#}); each flat moves it down a 5th ({C} {F} {Bb} {Eb} {Ab} {Db} {Gb} {Cb}).", "Le cycle des quintes : chaque dièse ajouté monte la tonalité d'une quinte ({C} {G} {D} {A} {E} {B} {F#} {C#}) ; chaque bémol la descend d'une quinte ({C} {F} {Bb} {Eb} {Ab} {Db} {Gb} {Cb})."),
  ("Five, six and seven sharps or flats are rarer but follow exactly the same rules.", "Cinq, six et sept altérations sont plus rares mais suivent exactement les mêmes règles."),
 ], None),
 L("Dorian and Mixolydian modes", "Les modes dorien et mixolydien", [
  ("A mode is a major scale played from another note. Same notes, different home.", "Un mode est une gamme majeure jouée à partir d'une autre note. Mêmes notes, autre point d'appui."),
  ("Dorian starts on the 2nd degree: {D} Dorian uses the notes of {C} major. It is a minor scale with a bright 6th, heard a lot in jazz and funk.", "Le dorien commence sur le 2e degré : {D} dorien utilise les notes de {C} majeur. C'est un mineur avec une 6te claire, très utilisé en jazz et funk."),
  ("Mixolydian starts on the 5th degree: {G} Mixolydian uses the notes of {C} major. It is major with a ♭7: the sound of blues and dominant chords.", "Le mixolydien commence sur le 5e degré : {G} mixolydien utilise les notes de {C} majeur. C'est un majeur avec une 7e mineure : le son du blues et des accords de dominante."),
 ], ["D4:1/8 E4:1/8 F4:1/8 G4:1/8 A4:1/8 B4:1/8 C5:1/8 D5:1/8", "G4:1/8 A4:1/8 B4:1/8 C5:1/8 D5:1/8 E5:1/8 F5:1/8 G5:1/8"], tip=("Find the major scale whose notes these are, then see which degree the run starts on.", "Trouvez la gamme majeure dont ce sont les notes, puis le degré sur lequel la série commence.")),
 L("All the modes", "Tous les modes", [
  ("From each degree of a major scale: 1 Ionian (major), 2 Dorian, 3 Phrygian, 4 Lydian, 5 Mixolydian, 6 Aeolian (natural minor), 7 Locrian.", "À partir de chaque degré : 1 ionien (majeur), 2 dorien, 3 phrygien, 4 lydien, 5 mixolydien, 6 éolien (mineur naturel), 7 locrien."),
  ("Phrygian: minor with a ♭2 ({E} Phrygian = notes of {C}). Lydian: major with a ♯4 ({F} Lydian = notes of {C}).", "Phrygien : mineur avec une 2de mineure ({E} phrygien = notes de {C}). Lydien : majeur avec une 4te augmentée ({F} lydien = notes de {C})."),
 ], ["E4:1/8 F4:1/8 G4:1/8 A4:1/8 B4:1/8 C5:1/8 D5:1/8 E5:1/8", "F4:1/8 G4:1/8 A4:1/8 B4:1/8 C5:1/8 D5:1/8 E5:1/8 F5:1/8"]),
]

FING = [
 L("Left hand: G A B C", "Main gauche : sol la si do", [
  ("The left hand plays the top keys: first finger (index) on the 1st pearl key, then middle and ring fingers on the 2nd and 3rd. The thumb rests under the octave key.", "La main gauche joue les clés du haut : l'index sur la 1re clé nacrée, puis le majeur et l'annulaire sur la 2e et la 3e. Le pouce repose sous la clé d'octave."),
  ("{B}: first finger. {A}: first and second. {G}: first, second and third.", "{B} : l'index. {A} : index et majeur. {G} : index, majeur et annulaire."),
  ("{C}: second finger only (middle key). Each finger you lift raises the note.", "{C} : le majeur seul (clé du milieu). Chaque doigt levé fait monter la note."),
 ], ["G4:1/4 A4:1/4 B4:1/4 C5:1/4"], tip=("In the drill, tap the keys on the drawing, or name the note a fingering plays.", "Dans l'exercice, touchez les clés sur le dessin, ou nommez la note d'un doigté.")),
 L("Both hands: D to C", "Les deux mains : ré à do", [
  ("The right hand adds three more pearl keys under the left hand: index, middle, ring.", "La main droite ajoute trois clés nacrées sous la main gauche : index, majeur, annulaire."),
  ("{F}: left hand 1-2-3 + right first finger. {E}: + right 1-2. {D}: all six fingers.", "{F} : main gauche 1-2-3 + index droit. {E} : + index et majeur droits. {D} : les six doigts."),
  ("The same three-finger pattern twice: the more fingers down, the lower the note.", "Le même schéma à trois doigts, deux fois : plus il y a de doigts posés, plus la note est grave."),
 ], ["D4:1/4 E4:1/4 F4:1/4 G4:1/4"]),
 L("The octave key", "La clé d'octave", [
  ("The left thumb presses the octave key to play the same fingering one octave higher.", "Le pouce gauche appuie sur la clé d'octave pour jouer le même doigté une octave plus haut."),
  ("From {D} in the staff (4th line) to high {D}, use the low fingering + octave key. {C#} in the middle has no fingers down at all.", "Du {D} de la portée (4e ligne) au {D} aigu, on garde le doigté grave + la clé d'octave. Le {C#} du milieu se joue sans aucun doigt."),
 ], ["D5:1/4 E5:1/4 F5:1/4 G5:1/4", "A5:1/4 B5:1/4 C6:1/4 D6:1/4"], tip=("Middle {D}: all six fingers + the octave key. Then lift fingers one by one to go up, exactly as in the low octave.", "{D} médium : les six doigts + la clé d'octave. Puis levez les doigts un par un pour monter, comme dans l'octave grave.")),
 L("Sharps and flats", "Dièses et bémols", [
  ("{F#}: right middle finger only (instead of the first). {Bb}: the bis key (small key between the first two of the left hand) or the side key.", "{F#} : majeur droit seul (au lieu de l'index). {Bb} : la clé bis (petite clé entre les deux premières de la main gauche) ou la clé latérale."),
  ("{G#}: left little finger on the {G#} key. {Eb}: right little finger on the {Eb} key with all fingers down.", "{G#} : auriculaire gauche sur la clé de {G#}. {Eb} : auriculaire droit sur la clé de {Eb}, tous les doigts posés."),
  ("{C#} (in the staff): no fingers. {F} and {Bb} have several fingerings: the chart shows the common ones.", "{C#} (dans la portée) : aucun doigt. {F} et {Bb} ont plusieurs doigtés : le tableau montre les plus courants."),
 ], ["F#4:1/4 G#4:1/4 Bb4:1/4 C#5:1/4"], tip=("Open the chart (top right) to compare fingerings.", "Ouvrez le tableau (en haut à droite) pour comparer les doigtés.")),
 L("The full range", "Toute la tessiture", [
  ("Low {C}, {B} and {Bb}: right little finger on low {C}; left little finger on low {B} and {Bb}, with all six fingers down.", "{C}, {B} et {Bb} graves : auriculaire droit sur le {C} grave ; auriculaire gauche sur {B} et {Bb} graves, avec les six doigts posés."),
  ("High {D} to {F#}: palm keys under the left hand (D, Eb, F) and the right side key (high E) and the front F, plus the octave key.", "{D} à {F#} aigus : clés de paume sous la main gauche (ré, mi♭, fa), clé latérale droite, clé de fa frontale, plus la clé d'octave."),
 ], ["Bb3:1/4 B3:1/4 C4:1/2", "D6:1/4 E6:1/4 F6:1/4 F#6:1/4"]),
]

EAR = [
 L("Higher or lower", "Plus aigu ou plus grave", [
  ("You hear two notes. Is the second one higher (up) or lower (down) than the first?", "Vous entendez deux notes. La deuxième est-elle plus aiguë (monte) ou plus grave (descend) que la première ?"),
  ("Sing or hum both notes quietly: feeling your voice go up or down helps a lot.", "Chantez ou fredonnez les deux notes : sentir votre voix monter ou descendre aide beaucoup."),
 ], None, tip=("With \"Answer on the sax\", play the note you heard instead of tapping.", "Avec « Répondre au sax », jouez la note entendue au lieu de toucher l'écran.")),
 L("First intervals", "Premiers intervalles", [
  ("An interval is the distance between two notes. Start with four very different ones: 2nd (a step), 3rd, 5th, octave.", "Un intervalle est la distance entre deux notes. Commencez par quatre très différents : seconde (un pas), tierce, quinte, octave."),
  ("Reference songs help: major 2nd = \"Happy Birthday\" (Hap-py), major 3rd = \"Oh When the Saints\", perfect 5th = \"Twinkle Twinkle\", octave = \"Somewhere over the Rainbow\".", "Des chansons repères aident : seconde majeure = « Joyeux anniversaire », tierce majeure = « Oh When the Saints », quinte = « Ah vous dirai-je maman », octave = « Over the Rainbow »."),
 ], None),
 L("More intervals", "Plus d'intervalles", [
  ("Minor 3rd: smaller and darker than the major 3rd (\"Greensleeves\" start). Perfect 4th: \"Here Comes the Bride\", the start of \"La Marseillaise\".", "Tierce mineure : plus petite et plus sombre que la majeure (début de « Greensleeves »). Quarte juste : « La Marseillaise » (Al-lons)."),
  ("Major 6th: wide and sweet (\"My Bonnie\"). Compare each new interval with the ones you already know.", "Sixte majeure : large et douce (« My Bonnie »). Comparez chaque nouvel intervalle avec ceux que vous connaissez."),
 ], None),
 L("Every interval", "Tous les intervalles", [
  ("Minor 2nd: very tense (\"Jaws\"). Tritone: unstable, between the 4th and the 5th (\"The Simpsons\", \"Maria\").", "Seconde mineure : très tendue (« Les Dents de la mer »). Triton : instable, entre la quarte et la quinte (« Les Simpson », « Maria »)."),
  ("Minor 6th and the 7ths: the 7ths sound like an octave that does not quite get there.", "Sixte mineure et septièmes : les septièmes sonnent comme une octave qui n'arrive pas tout à fait."),
 ], None),
 L("Major or minor chord", "Accord majeur ou mineur", [
  ("A chord is several notes together. Major sounds bright and stable; minor sounds darker or sadder.", "Un accord, c'est plusieurs notes ensemble. Le majeur sonne clair et stable ; le mineur plus sombre ou triste."),
  ("The difference is only the 3rd: one half step lower in the minor chord.", "La seule différence est la tierce : un demi-ton plus bas dans l'accord mineur."),
 ], None),
 L("Five chord types", "Cinq types d'accords", [
  ("Adding a 7th gives jazz colours: dominant 7 (major with ♭7, tense, wants to move: the blues chord), minor 7 (soft, open), major 7 (dreamy).", "Ajouter une septième donne les couleurs du jazz : 7 de dominante (majeur avec 7e mineure, tendu : l'accord du blues), mineur 7 (doux, ouvert), majeur 7 (rêveur)."),
  ("First decide major or minor, then listen to the top: tense (dominant), soft (minor 7) or shimmering (major 7).", "Décidez d'abord majeur ou mineur, puis écoutez le haut : tendu (dominante), doux (m7) ou brillant (maj7)."),
 ], None),
 L("Three-note melodies", "Mélodies de trois notes", [
  ("You hear three notes from a major scale. Find them one by one: the first, then up or down and how far.", "Vous entendez trois notes d'une gamme majeure. Trouvez-les une par une : la première, puis monte ou descend et de combien."),
  ("Replay as many times as you need. Singing the melody back before answering helps memory.", "Réécoutez autant que nécessaire. Rechanter la mélodie avant de répondre aide la mémoire."),
 ], None),
 L("Blues melodies", "Mélodies blues", [
  ("Four notes from the minor pentatonic / blues scale: 1, ♭3, 4, 5, ♭7, 8.", "Quatre notes de la pentatonique mineure / gamme blues : 1, 3♭, 4, 5, 7♭, 8."),
  ("These are the notes you use to improvise on a blues: recognising them by ear makes playing by ear much easier.", "Ce sont les notes de l'improvisation sur un blues : les reconnaître à l'oreille rend le jeu d'oreille bien plus facile."),
 ], ["C5:1/8 Eb5:1/8 F5:1/8 G5:1/8 Bb5:1/8 C6:1/8 r:1/4"]),
]

RHYTHM = [
 L("Quarter, half and whole notes", "Noires, blanches et rondes", [
  ("In 4/4 there are four beats in a bar. A quarter note lasts 1 beat, a half note 2, a whole note 4.", "En 4/4 il y a quatre temps par mesure. Une noire dure 1 temps, une blanche 2, une ronde 4."),
  ("How to play: after 4 clicks, tap the pad exactly when each note starts. For notes of 2 beats or more (half and whole notes), keep your finger down until the note ends.", "Comment jouer : après 4 clics, touchez exactement quand chaque note commence. Pour les notes de 2 temps ou plus (blanches et rondes), gardez le doigt appuyé jusqu'à la fin de la note."),
  ("The bar goes by twice in a row; your better pass counts. Each tap appears as a dot on the ruler: green on time, amber a little off, red off.", "La mesure passe deux fois de suite ; votre meilleur passage compte. Chaque appui apparaît comme un point sur la règle : vert à temps, orange un peu décalé, rouge décalé."),
  ("Under the staff, the counting is written out: \"1 –\" means the note starts on beat 1 and lasts through beat 2. Count it in your head with the clicks.", "Sous la portée, le comptage est écrit : « 1 – » veut dire que la note commence sur le temps 1 et dure pendant le temps 2. Comptez-le dans votre tête avec les clics."),
 ], ["B4:1/4 B4:1/4 B4:1/2", "B4:1/1"], tip=("Tap with the click, not after it: musicians feel the beat coming. Use \"Listen first\" as often as you like.", "Touchez avec le clic, pas après : les musiciens sentent le temps arriver. Utilisez « Écouter d'abord » autant que vous voulez.")),
 L("Rests", "Les silences", [
  ("A rest is silence of a certain length. A quarter rest lasts 1 beat: do not press, keep counting.", "Un silence est un temps sans son. Un soupir dure 1 temps : n'appuyez pas, continuez de compter."),
 ], ["B4:1/4 r:1/4 B4:1/4 r:1/4", "r:1/4 B4:1/2 B4:1/4"]),
 L("Eighth notes", "Les croches", [
  ("Two eighth notes fill one beat: count \"1 and 2 and 3 and 4 and\".", "Deux croches remplissent un temps : comptez « 1 et 2 et 3 et 4 et »."),
  ("Each eighth is a short tap, in time with \"1\" and \"and\". Short notes only need their start to be right.", "Chaque croche est un appui court, sur « 1 » et sur « et ». Pour les notes courtes, seul le début compte."),
 ], ["B4:1/8 B4:1/8 B4:1/8 B4:1/8 B4:1/4 B4:1/4"]),
 L("Dotted quarter notes", "Les noires pointées", [
  ("A dot adds half the note's length: a dotted quarter lasts 1½ beats, and is usually followed by an eighth.", "Un point ajoute la moitié de la durée : une noire pointée dure 1 temps et demi, souvent suivie d'une croche."),
  ("Tap on 1, count \"2\" silently, and tap the eighth exactly on the \"and\" of 2.", "Touchez sur 1, comptez « 2 » en silence, et touchez la croche exactement sur le « et » du 2."),
 ], ["B4:3/8 B4:1/8 B4:1/2"]),
 L("Syncopation", "La syncope", [
  ("A syncopation starts a note between the beats and holds it over the next beat: eighth, quarter, eighth.", "Une syncope commence une note entre les temps et la tient par-dessus le temps suivant : croche, noire, croche."),
  ("Feel the beat with the clicks and press on the \"and\": it is the sound of funk, latin and jazz.", "Sentez le temps avec les clics et appuyez sur le « et » : c'est le son du funk, du latin et du jazz."),
 ], ["B4:1/8 B4:1/4 B4:1/8 B4:1/2"]),
 L("Sixteenth notes", "Les doubles croches", [
  ("Four sixteenths fill one beat: count \"1 e and a\". The tempo is slower so you can place them.", "Quatre doubles croches remplissent un temps : comptez « 1 e et a ». Le tempo est plus lent pour pouvoir les placer."),
  ("Mixed groups: eighth + two sixteenths (\"1 . and a\"), or two sixteenths + eighth (\"1 e and\").", "Groupes mixtes : croche + deux doubles (« 1 . et a »), ou deux doubles + croche (« 1 e et »)."),
 ], ["B4:1/16 B4:1/16 B4:1/16 B4:1/16 B4:1/8 B4:1/16 B4:1/16 B4:1/2"]),
 L("Swing eighths", "Les croches swing", [
  ("In jazz and blues, eighths are written straight but played long-short: the first takes two thirds of the beat, the second one third.", "En jazz et en blues, les croches s'écrivent égales mais se jouent longue-courte : la première prend deux tiers du temps, la seconde un tiers."),
  ("Think \"doo-ba doo-ba\", or the triplet \"1 - a 2 - a\". Listen first to hear the feel.", "Pensez « dou-ba dou-ba », ou le triolet « 1 - a 2 - a ». Écoutez d'abord pour sentir le balancement."),
 ], ["B4:1/8 B4:1/8 B4:1/8 B4:1/8 B4:1/2"]),
]

out = {"READING": READING, "SCALE_RECOGNITION": SCALE, "FINGERING": FING, "EAR": EAR, "RHYTHM": RHYTHM}
json.dump(out, open(__import__('os').path.join(__import__('os').path.dirname(__file__), '..', '..', 'app/src/main/assets/drill_lessons/lessons.json'), 'w'), ensure_ascii=False, indent=1)
print({k: len(v) for k, v in out.items()})
