(ns spicy-github.frontend
    (:require
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [stylefy.rum :as stylefy-rum]
        [spicy-github.api :as api]))

(defn frontend-initialize! [] (stylefy/init {:dom (stylefy-rum/init)}))

(frontend-initialize!)

(defn add-font-faces! []
    (stylefy/font-face {:font-family "'open_sans'"
                        :src         "url('./fonts/OpenSans-Regular.woff2') format('woff2'), url('./fonts/OpenSans-Regular.woff') format('woff'), url('./fonts/OpenSans-Regular.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"})
    (stylefy/font-face {:font-family "'open_sans_light'"
                        :src         "url('./fonts/OpenSans-Light.woff2') format('woff2'), url('./fonts/OpenSans-Light.woff') format('woff'), url('./fonts/OpenSans-Light.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"})
    )

(add-font-faces!)

(def body-style {:font-family "'open_sans', 'Courier New'"
                 })

(def comment-style {:border-radius :10px
                    :margin        :10px
                    :color         :#fff
                    :display       :flex})

(def comment-container-style {:font-family      "'open_sans_light', 'open_sans', 'Courier New'"
                              :display          :flex
                              :background-color :#333
                              :color            :#fff
                              :padding          "5px 15px 5px 15px"
                              :border-radius    :20px
                              :margin           :5px
                              :opacity          :0.8
                              :cursor           :auto})

(def comment-body-style {:flex    :9
                         :display :inline
                         :padding "0px 5px 0px 5px"})

(def issue-header-style {
                         :text-align :center
                         })

(def issue-style {:border-radius    :20px
                  :margin-bottom    :20px
                  :display          :flex
                  :box-sizing       :border-box
                  :background-color :#ccc
                  :color            :#333
                  :max-width        :1000px
                  :margin           :auto
                  :flex-direction   :column})

(def issue-body-style {:flex    :9
                       :padding "5px 5px 20px 20px"
                       :cursor  :pointer})

(def issue-container-style {:display      :flex
                            :margin-left  :10px
                            :margin-right :10px})

(def issue-user-image-style {:background-color :#fff
                             :border-radius    :50%
                             ::stylefy/media   [[{:min-width :100px} {:width  :50px
                                                                      :height :50px
                                                                      :flex   "0 0 50px"}]
                                                [{:min-width :500px} {:width  :100px
                                                                      :height :100px
                                                                      :flex   "0 0 100px"}]]
                             :padding          :10px
                             :margin-bottom    :10px
                             :margin-top       :10px
                             :margin-right     :10px
                             :margin-left      :auto})

(def user-image-style {:background-color :#fff
                       :border-radius    :50%
                       ::stylefy/media   [[{:min-width :100px} {:width  :50px
                                                                :height :50px
                                                                :flex   "0 0 50px"}]
                                          [{:min-width :500px} {:width  :100px
                                                                :height :100px
                                                                :flex   "0 0 100px"}]]
                       :padding          :10px
                       :margin-top       :10px
                       :margin-bottom    :10px})

(def issue-title-text-style {
                             :text-decoration :none
                             :color           :#5c55fc
                             :font-weight     :bold
                             })

(defn get-user-html
    ([user] (get-user-html user user-image-style))
    ([user style]
     [:img (merge (stylefy/use-style style) {:src (:user/avatar-url user)})]))

(defn get-comment-html [comment]
    [:div (stylefy/use-style comment-style)
     (-> comment :comment/user get-user-html)
     [:div (stylefy/use-style comment-container-style)
      [:div (stylefy/use-style comment-body-style)
       [:md-block (:comment/body comment)]]]])

(defn get-ordered-comments [comments]
    (let [ordered-by-date-comments (sort-by :comment/updated-at comments)
          root-comment (last (filter (fn [comment] (-> comment :comment/parent-comment nil?)) ordered-by-date-comments))
          comments-with-parents (filter (fn [comment] (-> comment :comment/parent-comment nil? not)) ordered-by-date-comments)
          comments-by-parent-id (into {} (map vector (map :comment/parent-comment comments-with-parents) comments-with-parents))]
        (loop [chain []]
            (if (empty? chain)
                (if (nil? root-comment)
                    chain
                    (let [matching (get comments-by-parent-id (:comment/id root-comment))]
                        (if (nil? matching)
                            chain
                            (recur (conj chain matching)))))
                (let [matching (get comments-by-parent-id (:comment/id (last chain)))]
                    (if (nil? matching)
                        chain
                        (recur (conj chain matching)))
                    )
                ))
        ))

(defn get-issue-html [issue]
    [:div (stylefy/use-style issue-style)
     [:h1 (stylefy/use-style issue-header-style)
      [:a (merge (stylefy/use-style issue-title-text-style) {:href (:issue/url issue)}) (:issue/title issue)]]
     [:details
      [:summary [:div (stylefy/use-style issue-container-style)
                 [:md-block (stylefy/use-style issue-body-style) (:issue/body issue)]
                 (-> (:issue/user issue) (get-user-html issue-user-image-style))]]
      (vec (conj (->> (:issue/comments issue) get-ordered-comments (map get-comment-html)) :div))
      ]
     ])

(defn get-issues-html [issues]
    [:div (vec (conj (map get-issue-html issues) :div))])

;; https://gist.github.com/nberger/b5e316a43ffc3b7d5e084b228bd83899

(defn- get-scroll-top []
    (if (exists? (.-pageYOffset js/window))
        (.-pageYOffset js/window)
        (.-scrollTop (or (.-documentElement js/document)
                         (.-parentNode (.-body js/document))
                         (.-body js/document)))))

(defn- get-top-position [node]
    (if (not node)
        0
        (+ (.-offsetTop node) (get-top-position (.-offsetParent node)))))

(defn debounce
    "Returns a function that will call f only after threshold has passed without new calls
    to the function. Calls prep-fn on the args in a sync way, which can be used for things like
    calling .persist on the event object to be able to access the event attributes in f"
    ([threshold f] (debounce threshold f (constantly nil)))
    ([threshold f prep-fn]
     (let [t (atom nil)]
         (fn [& args]
             (when @t (js/clearTimeout @t))
             (apply prep-fn args)
             (reset! t (js/setTimeout #(do
                                           (reset! t nil)
                                           (apply f args))
                                      threshold))))))

(def issues (atom []))

(def can-load-more (atom true))

(defn- update-issues! [new-issues]
    (if (empty? new-issues)
        (reset! can-load-more false)
        (reset! issues (concat @issues new-issues))))

(api/get-n-issues-before update-issues!)

(defn- try-initialize-issues! []
    (when (empty? @issues)
        (api/get-n-issues-before update-issues!)))

(defn- load-fn []
    (api/get-n-issues-before-from-issues @issues update-issues!))

(def listener-fn (atom nil))

(defn- detach-scroll-listener []
    (when @listener-fn
        (.removeEventListener js/window "scroll" @listener-fn)
        (.removeEventListener js/window "resize" @listener-fn)
        (reset! listener-fn nil)
        (reset! can-load-more true)))

(defn- should-load-more? [state]
    (let [node (rum/dom-node state)
          scroll-top (get-scroll-top)
          my-top (get-top-position node)
          threshold 50]
        (< (- (+ my-top (.-offsetHeight node))
              scroll-top
              (.-innerHeight js/window))
           threshold)))

(defn- scroll-listener [state]
    (println (str "Scrolling! State: '" state "'"))
    (when (and @can-load-more (should-load-more? state))
        (println "loading more...")
        (detach-scroll-listener)
        (load-fn)))

(defn- debounced-scroll-listener [] (debounce 200 scroll-listener))

(defn- attach-scroll-listener [state]
    (println "Attaching scroll listener...")
    (when should-load-more? state
                            (when-not @listener-fn
                                (reset! listener-fn (partial debounced-scroll-listener state))
                                (.addEventListener js/window "scroll" @listener-fn)
                                (.addEventListener js/window "resize" @listener-fn))))

(rum/defcs issues-stateful-component
    <
    rum/reactive
    {:did-mount    (fn [state] (attach-scroll-listener state))
     :did-update   (fn [state] (attach-scroll-listener state))
     :will-unmount detach-scroll-listener}
    [state]
    (get-issues-html @issues))

(rum/mount (issues-stateful-component) (.getElementById js/document "issues-container"))

(js/setInterval
    #(rum/mount (issues-stateful-component) (.getElementById js/document "issues-container"))
    1000)

(js/setInterval
    try-initialize-issues!
    5000)