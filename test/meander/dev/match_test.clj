(ns meander.dev.match-test
  (:require [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as tc.t]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as s.gen]
            [meander.dev.syntax :as r.syntax]
            [meander.dev.match :as r.match]))

(defn gen-parse-tree [form-gen]
  (s.gen/fmap r.syntax/parse form-gen))


(defn gen-row [form-gen]
  (s.gen/fmap
   (fn [[col rhs]]
     {:cols [col]
      :rhs rhs})
   (s.gen/tuple
    (gen-parse-tree form-gen)
    (s.gen/keyword))))


(defn gen-rows [form-gen]
  (s.gen/such-that not-empty (s.gen/vector (gen-row form-gen))))


(t/deftest match-test
  (let [is (shuffle (range 5))
        js (shuffle (range 5))
        ms (map (fn [i j] {:i i, :j j}) is js)]
    (t/is
     (r.match/match ms
       ({:i !is, :j !js} ...)
       (and (= !is is)
            (= !js js))

       _
       false)))
  
  (t/is
   (r.match/match (range -5 6)
     (!xs ... . 3 4 5)
     (= !xs (range -5 3))

     _
     false))

  (t/is
   (r.match/match '(1 2 1 2 4)
     (?x ?y ... . 4)
     true
     _
     false))

  (t/is
   (not
    (r.match/match '(1 2 1 3 4)
      (?x ?y ... . 4)
      true

      _
      false)))

  (t/is
   (r.match/match [1 2 1 2 4]
     [?x ?y ... . 4]
     true
     _
     false))
  
  (t/is
   (not
    (r.match/match [1 2 1 3 4]
      [?x ?y ... . 4]
      true

      _
      false)))

  (t/testing "and patterns"
    (t/is
     (r.match/match [1 2 3]
       (and ?x [_ ...])
       (= ?x [1 2 3])))

    (let [xs [[1 2 3] [1 2 3] [1 2 3]]]
      (t/is
       (r.match/match xs
         [?x ?y ?x]
         (= ?x ?y [1 2 3])

         _
         false))

      (t/is
       (r.match/match xs
         [?x (and ?x ?y) ?y]
         (= ?x ?y [1 2 3])

         _
         false))

      (t/is
       (r.match/match xs
         [?x ?y (and ?x ?y)]
         (= ?x ?y [1 2 3])

         _
         false))))

  (t/testing "init patterns"
    (t/is
     (r.match/match '(1 2 3 4 5 6)
       (_ ... . 4 5 6)
       true

       _
       false))

    (t/is
     (not
      (r.match/match '(1 2 3 4 5 6)
        (_ ... . 4 5)
        true

        _
        false)))

    (t/is
     (r.match/match [1 2 3 4 5 6]
       [_ ... . 4 5 6]
       true

       _
       false))

    (t/is
     (not
      (r.match/match [1 2 3 4 5 6]
        [_ ... . 4 5]
        true

        _
        false))))

  (t/testing "predicate patterns"
    (t/is
     (r.match/match [1 2 3]
       (? vector?)
       true

       _
       false))

    (t/is
     (r.match/match "foo"
       (? (fn [x] (re-matches #"[a-z]+" x)))
       true

       _
       false)))

  (t/testing "map patterns"
    (let [node {:tag :h1
                :attrs {:style "font-weight:normal"}
                :children []}]
      (t/is
       (r.match/match node
         {:tag ?tag, :attrs ?attrs, :children ?children}
         (and (= (get node :tag)
                 ?tag)
              (= (get node :attrs)
                 ?attrs)
              (= (get node :children)
                 ?children))

         _
         false))

      ;; TODO: The most specific match should be selected. 
      (t/is
       (not
        (r.match/match node
          {:tag ?tag, :attrs ?attrs, :children ?children}
          (and (= (get node :tag)
                  ?tag)
               (= (get node :attrs)
                  ?attrs)
               (= (get node :children)
                  ?children))

          {:tag ?tag, :children ?children}
          false

          {:tag ?tag, :attrs ?attrs}
          false

          {:attrs ?attrs, :children ?children}
          false

          {:attrs ?attrs}
          false

          {:children ?children}
          false)))))

  (t/testing "drop patterns"
    (t/is
     (r.match/match '(1 2 3 4 5 6)
       (1 2 3 . _ ...)
       true

       _
       false))

    (t/is
     (r.match/match [1 2 3 4 5 6]
       [1 2 3 . _ ...]
       true

       _
       false))

    (t/is
     (not
      (r.match/match '(1 2 3 4 5 6)
        (1 2 3 4 5 6 7 . _ ...)
        true

        _
        false)))

    (t/is
     (not
      (r.match/match [1 2 3 4 5 6]
        [1 2 3 4 5 6 7 . _ ...]
        true

        _
        false))))


  (let [form '(let (a 1 b 2)
                (+ a b)
                (+ b a))]
    (t/is
     (r.match/match form
       (let . !forms ...)
       (= !forms '[(a 1 b 2) (+ a b) (+ b a)])

       _
       false))

    (t/is
     (r.match/match form
       (let ?bindings . !forms ...)
       (and (= ?bindings '(a 1 b 2))
            (= !forms '[(+ a b) (+ b a)]))

       _
       false))

    (t/is
     (r.match/match form
       (let (!bindings ...) . !forms ...)
       (and (= !bindings '(a 1 b 2))
            (= !forms '[(+ a b) (+ b a)]))

       _
       false))

    (t/is
     (r.match/match form
       (let (!syms !vals ...) . !forms ...)
       (and (= !syms '(a b))
            (= !vals '(1 2))
            (= !forms '[(+ a b) (+ b a)]))

       _
       false))

    (t/is
     (r.match/match form
       (let ((!syms !vals :as !pairs) ...) . !forms ...)
       (and (= !syms '(a b))
            (= !vals '(1 2))
            (= !forms '[(+ a b) (+ b a)]))

       _
       false)))

  (t/is 
   (r.match/match '(let [x 1, y 1]
                     (+ x y))
     (let [!bindings ...] . !body ...)
     (and (= !bindings '[x 1, y 1])
          (= !body '[(+ x y)]))

     _
     false))

  (t/testing "cap patterns"
    (t/is
     (let [n (rand)]
       (r.match/match [n n]
         [(_ :as ?x) (_ :as ?x)]
         (= n ?x))))

    (t/is
     (let [n (rand)]
       (r.match/match [n n]
         [(_ :as !x) (_ :as !x)]
         (= [n n] !x))))

    (t/is
     (let [n 1]
       (r.match/match [n n]
         [(_ _ :as !x)]
         (= [[n n]] !x))))

    (t/is
     (r.match/match '[x 0 y 1 1 2 3]
       [(!bindings !values :as !binding-pairs) ...
        . (1 2 3 :as ?tail)]
       (and (= !bindings
               '[x y])
            (= !values
               [0 1])
            (= '[[x 0] [y 1]]
               !binding-pairs)
            (= [1 2 3]
               ?tail))
       _
       false))

    (t/is
     (r.match/match '(let [a 1 b 2]
                       (+ a b)
                       (+ b a))
       (let [(!bindings !values :as !binding-pairs) ...] . !body ...)
       (and (= '[a b]
               !bindings)
            (= '[1 2]
               !values)
            (= '[(a 1) (b 2)]
               !binding-pairs)
            (= '[(+ a b) (+ b a)]
               !body))))

    
    (t/is
     (not
      (r.match/match (list 1 2 1 2 1 3 1 4)
        ;; This is a "search" because it has variable length on both
        ;; sides. It should fail to match.
        (?x ?y ... . !zs ...)
        (and (= ?x 1)
             (= ?y 2)
             (= !zs [1 3 1 4]))

        _
        false)))

    (t/is
     (r.match/match '(def foo "bar" :baz)
       (def ?sym ?init)
       (and (= ?sym 'foo)
            (= ?init :baz))

       (def ?sym ?doc ?init)
       (and (= ?sym 'foo)
            (= ?doc "bar")
            (= ?init :baz))


       _
       false))))
