(ns reframe-frontend.flagencoding)

(defn encode [x y]
  (let [adjusted (+ (count y) 1)]
      (if (= 0 x)
        (reverse y)
        (encode (quot x adjusted)
                (conj y (rem x adjusted))))))

(defn factorial
  [n]
  (if (<= n 1)
    1
    (* n (factorial (dec n)))))

(defn decode [x acc]
  (if (not-empty x)
    (decode (rest x)
            (+ (* (first x) (factorial (- (count x) 1))) acc))
    acc))

(decode (encode 463 []) 0)

(defn char-to-ascii-map
  [x]
  (mapv #(.charCodeAt %) x))

(defn ascii-map-to-string
  [x]
  (reduce str (mapv #(.fromCharCode js/String %) x)))

(ascii-map-to-string (char-to-ascii-map "regular"))

(map #(decode % 0) (mapv #(encode % []) (char-to-ascii-map "regular")))

;; (mod 2 463)

;; (quot 463 2)
