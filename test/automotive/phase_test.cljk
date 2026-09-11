(ns automotive.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-vehicle`/`:actuation/issue-
  conformity-certificate` must NEVER be a member of any phase's
  `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [automotive.phase :as phase]))

(deftest dispatch-vehicle-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real robot vehicle dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-vehicle))
          (str "phase " n " must not auto-commit :actuation/dispatch-vehicle")))))

(deftest issue-conformity-certificate-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real Certificate of Conformity"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-conformity-certificate))
          (str "phase " n " must not auto-commit :actuation/issue-conformity-certificate")))))

(deftest end-of-line-quality-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :end-of-line-quality/screen))
          (str "phase " n " must not auto-commit :end-of-line-quality/screen")))))

(deftest robotics-simulate-assembly-line-never-auto-at-any-phase
  (testing "the robot CAE/assembly-line verification mission carries no direct capital risk, but is still never auto-eligible, matching every sibling verification op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :robotics/simulate-assembly-line))
          (str "phase " n " must not auto-commit :robotics/simulate-assembly-line")))))

(deftest robotics-simulate-assembly-line-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :robotics/simulate-assembly-line))
  (is (contains? (:writes (get phase/phases 3)) :robotics/simulate-assembly-line))
  (is (not (contains? (:writes (get phase/phases 1)) :robotics/simulate-assembly-line))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":vehicle/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:vehicle/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :vehicle/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-vehicle} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-conformity-certificate} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :vehicle/intake} :commit)))))
