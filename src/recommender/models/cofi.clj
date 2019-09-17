(ns recommender.models.cofi
  {:author "Alex Gherega" :doc "Collaborative filtering recommender model"}
  
  (:require [clojure.core.matrix :as m]
            [fastmath.core :as fmc]
            [fastmath.stats :as fms]))

(m/set-current-implementation :vectorz)

;; cost function
(defn linear-cost [x y r theta]
  (let [sparse (m/mul (m/sub (m/mmul x (m/transpose theta)) y)
                      r)]
    
   
    (/ (m/esum (m/mul sparse sparse))
       2)))

(defn regularize-cost [x theta lambda init-cost]
  ;; original
  ;; (+ init-cost (* (/ lambda 2) (+ (m/esum (m/diagonal (m/mmul theta (m/transpose theta))))
  ;;                                 (m/esum (m/diagonal (m/mmul x (m/transpose x)))))))

  ;; efficient?
  (+ init-cost
     (* lambda 0.5 (+ (apply + (pmap m/dot theta theta))
                      (apply + (pmap m/dot x x))))))

(defn regularized-linear-cost [x y r theta lambda]
  (regularize-cost x theta lambda (linear-cost x y r theta)))

;; grandient functions

;; gradient computation for the features space
(defmacro gradient-xline [theta x-row y-row r-row]
  `(let [;;original
         ;; tmp-theta (m/mul theta (m/transpose (m/broadcast r-row (-> theta m/shape reverse))))
         ;; efficient?
         tmp-theta# (mapv m/scale ~theta ~r-row)
         ]
     (m/mmul (m/sub (m/mmul ~x-row (m/transpose tmp-theta#))
                    ~y-row)
             tmp-theta#)))

(defn- scale-byzero [m binary-v]
  (m/sparse-matrix (mapv #(if (zero? %2) (m/scale %1 0)
                              %1)
                         m
                         binary-v)))

(defn- gradient-xline [theta x-row y-row r-row]
  (let [;;original
        ;; tmp-theta (m/mul theta (m/transpose (m/broadcast r-row (-> theta m/shape reverse))))
        ;; efficient?
        tmp-theta (scale-byzero theta r-row) ;;(mapv m/scale theta r-row)
        ]
    (m/mmul (m/sub (m/mmul x-row (m/transpose tmp-theta))
                   y-row)
            tmp-theta)))

(defn grad-x [x y r theta]
  (pmap (partial gradient-xline theta) x y r))


;; gradient computation for the hyperparams space
(defn- gradient-thetaline [x y-col r-col theta-row]
  (let [;; original
        ;; tmp-x (m/mul x (m/transpose (m/broadcast r-col (-> x m/shape reverse))))
        ;;efficient?
         tmp-x (scale-byzero x r-col) ;;(mapv m/scale x r-col)
        ;; _ (prn 
        ;;        ;; (first (m/mmul tmp-x theta-row))
        ;;    ;; (filter #(.equals % ##NaN) (m/sub (m/mmul tmp-x theta-row)
        ;;    ;;                                   y-col))
        ;;    ;; (first (m/mmul (m/sub (m/mmul tmp-x theta-row)
        ;;    ;;                       y-col)
        ;;    ;;                tmp-x))
        ;;    (filter #(.equals % ##NaN) y-col)
        ;;    )
        ]
    (m/mmul (m/sub (m/mmul tmp-x theta-row)
                   y-col)
            tmp-x)))

(defn grad-theta [x y r theta]
  (pmap (partial gradient-thetaline x) 
        y ;; y is actualy in transpose form
        r ;; r is actualy in transpose form
        theta))

(defn regularize-grad [varm lambda init-grad]
  (m/add init-grad (m/scale varm lambda)))

;; regularized graiend functions
(defn regularized-gradx [x y r theta lambda]    
  (regularize-grad x lambda (grad-x x y r theta)))

(defn regularized-gradtheta [x y r theta lambda]
  (regularize-grad theta lambda (grad-theta x
                                            (m/transpose y) ;;y 
                                            (m/transpose r) ;;r
                                            theta)))

;; training by gradient descent
(defn- update-rule [in in-gradient alpha]
  (m/sub in (m/scale in-gradient alpha)))

(defn gd-train [x y r theta
                {:keys [rcost-f rgx-f rgtheta-f] :as r-fns}
                {:keys [lambda alpha epsilon no-iters] :as params}
                ;;rcost-f rgx-f rgtheta-f n
                ] ;; naive gradient descent trainig

  (loop [;; results
         x x
         theta theta
         n (:no-iters params)
         prev-cost 0]
    (prn "Iteration no: " n
         (-> theta first first)
         (-> x first first))
    (if (or (zero? n)
            (< (fmc/abs (- prev-cost (rcost-f x y r theta lambda)))
               epsilon))
      (do (prn "Iterations spent: " n) [x theta])
      (recur (update-rule x (rgx-f x y r theta lambda) alpha)
             (update-rule theta (rgtheta-f x y r theta lambda) alpha)
             (dec n)
             (rcost-f x y r theta lambda)))))