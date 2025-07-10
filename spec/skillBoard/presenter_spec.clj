(ns skillBoard.presenter-spec
  (:require [skillBoard.presenter :as p]
            [speclj.core :refer :all]
            ))

(def ref-lat 42)
(def ref-lon -87)

(describe "find-location"
  (it "handles degenerate cases"
    (should= "" (p/find-location 0 0 0 [])))

  (it "returns no position when outside of geo-fences"
    (should= "" (p/find-location ref-lat ref-lon 1700
                                  [
                                   {:lat (inc ref-lat)
                                    :lon (inc ref-lon)
                                    :radius 1
                                    :min-alt 720
                                    :max-alt 2000
                                    :name "NAME"}]))
    (should= "" (p/find-location ref-lat ref-lon 3000
                                  [
                                   {:lat ref-lat
                                    :lon ref-lon
                                    :radius 1
                                    :min-alt 720
                                    :max-alt 2000
                                    :name "NAME"}]))
    (should= "" (p/find-location ref-lat ref-lon 700
                                  [
                                   {:lat ref-lat
                                    :lon ref-lon
                                    :radius 1
                                    :min-alt 1000
                                    :max-alt 2000
                                    :name "NAME"}]))
    )
  (it "finds first geofence"
    (should= "NAME" (p/find-location ref-lat ref-lon 1100
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 1000
                                       :max-alt 2000
                                       :name "NAME"}]))

    (should= "NAME" (p/find-location ref-lat ref-lon 1100
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 720
                                       :max-alt 1000
                                       :name "NO"}
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 1099
                                       :max-alt 2000
                                       :name "NAME"}]))
    (should= "NAME" (p/find-location ref-lat ref-lon 1200
                                         [
                                          {:lat ref-lat
                                           :lon ref-lon
                                           :radius 1
                                           :min-alt 720
                                           :max-alt 1000
                                           :name "NO"}
                                          {:lat (inc ref-lat)
                                           :lon (inc ref-lon)
                                           :radius 100
                                           :min-alt 1099
                                           :max-alt 2000
                                           :name "NAME"}])))
  )