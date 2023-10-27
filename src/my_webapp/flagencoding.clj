(ns my-webapp.flagencoding)

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
  (mapv #(int %) x))

(defn ascii-map-to-string
  [x]
  (reduce str (mapv #(char %) x)))

(defn permutation-of-encode
  [z]
  (let
      [x (into [] (map identity z))]
      (loop [perm (into [] (range 0 (count x)))
             y []]
        (prn perm)
        (if (= (count x) (count y))
          y
          (recur (into [] (remove #(= % (get perm (get x (count y))))) perm)
                 (conj y (get perm (get x (count y)))))))))

(mapv permutation-of-encode (mapv #(encode % []) (char-to-ascii-map "regular")))

(ascii-map-to-string (char-to-ascii-map "regular"))

(map #(decode % 0) (mapv #(encode % []) (char-to-ascii-map "regular")))

(into [] (map identity) (list 1 23))
