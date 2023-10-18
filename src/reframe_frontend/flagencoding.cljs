(ns reframe-frontend.flagencoding)

;; (defn encode [x y]
;;   (if (= 0 x)
;;     y
;;     (prn (quot x (count y)) (conj y (rem x y)))))

(defn encode [x y]
  (let [adjusted (+ (count y) 1)]
      (if (= 0 x)
        y
        (encode (quot x adjusted)
                (conj y (rem x adjusted))))))

(encode 463 [])

;; (mod 2 463)

;; (quot 463 2)
