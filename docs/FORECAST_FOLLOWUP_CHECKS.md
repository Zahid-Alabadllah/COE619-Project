# Bounded forecasting follow-up

Recorded 18 November 2026. These checks use previously inspected inputs and are exploratory, not new independent validation. No new Java model was adopted.

## Persistence blend

Tested moving partway from unchanged CPU toward bounded adaptive trend, learning that amount from the last20 strictly earlier verified errors. It starts with unchanged CPU until ten errors are available. Objective is squared error; report MAE too.

On the first Bitbrains block, the blend has MAE0.1926/RMSE0.7657pp versus persistence0.1948/0.7479. MAE is slightly better, but larger-error performance is worse. On the separate block it has1.6594/7.2286 versus bounded adaptive1.6686/7.1532, again a trade-off. It also trails the existing bounded trend/mean selector on the reused PlanetLab population/stress inputs. The blend was not promoted.

## Two-reading regression

Tested forecasting from the last two readings rather than one, with20 earlier training triples and a small ridge penalty toward unchanged CPU. Warmup uses unchanged CPU; outputs remain within0–100%. The fixed penalty is a design choice, not a tuned universal constant. This is a conventional autoregression-style experiment, not a novelty claim.

It scored MAE/RMSE0.2198/1.0157pp on the first Bitbrains block and2.0560/7.6623 on the separate block. These do not beat the simpler controls. It also trails the existing selector on the reused PlanetLab sets. Do not add extra coefficients to the runtime merely because this model was tried.

Plans, exact settings and metrics are retained in persistence_blend and ar2_development. Scripts are research tools outside the five production Java files. No more reserved VM outcomes were consumed. Next model choice should have a primary loss metric fixed before using reserved data; MAE, tail error, warning precision and energy are separate objectives.
