(ns huutopussi-client.translation)

(def suits-fi
  {"diamonds" "ruutu"
   "hearts" "hertta"
   "spades" "pata"
   "clubs" "risti"})

(def card-text-genitive-fi
  {"J" "jätkän"
   "A" "ässän"
   "K" "kuninkaan"
   "Q" "rouvan"
   "10" "kympin"
   "9" "ysin"
   "8" "kasin"
   "7" "seiskan"
   "6" "kutosen"})

(def card-text-adessive-fi
  {"J" "jätkällä"
   "A" "ässällä"
   "K" "kuninkaalla"
   "Q" "rouvalla"
   "10" "kympillä"
   "9" "ysillä"
   "8" "kasilla"
   "7" "seiskalla"
   "6" "kutosella"})

(defn format-card [{:keys [text suit]} grammatical-case]
  (case grammatical-case
    :genitive (str (get suits-fi suit) (get card-text-genitive-fi text))
    :adessive (str (get suits-fi suit) (get card-text-adessive-fi text))))
