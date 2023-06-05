(ns huutopussi-simulation.presentation)

;; Me
;; --

;; Markus Hjort: A coder from Helsinki, Finland

















; What is Load testing?
; ---------------------



; LT = Load Testing tool
; SUT = System Under Test

; |-----| -- simulate-user1 ---> |-----|
; | LT  |                        | SUT |
; |-----| -- simulate-user2 ---> |-----|
;
;    |                              |
;   \ /                            \ /
;    V                              V
;
; Benchmark                      Performance
; Results                        metrics (APM)
;


;; Idea is to simulate how concurrent users use the SUT

;; And see how it behaves under the load
