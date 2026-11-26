"""Plot the validated 27 November research checkpoint, without changing evidence."""
import csv
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

CODE = Path(__file__).resolve().parents[2]
SOURCE = CODE / 'results/predictor_investigation/online_holdout_20110420/readonly_validated_matrix/experiments.csv'
OUT = CODE.parent / 'Course_Documents/02_Progress_Meetings/2026-11-27_Research_Progress'


def main():
    rows = {(r['scenario'], r['policy']): r for r in csv.DictReader(SOURCE.open())}
    cases = ['diurnal', 'real_a', 'real_b', 'real_c', 'heavy']
    labels = ['Day/night', 'Real A', 'Real B', 'Real C', 'Heavy real']
    colors = ['#42697d', '#007f6b', '#d99b38']
    bg, ink, muted = '#f6f8f4', '#17313d', '#5d717a'
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 13, 'svg.fonttype': 'none'})
    fig = plt.figure(figsize=(16, 9), facecolor=bg)
    fig.text(.055, .91, 'Current energy comparison', fontsize=30, weight='bold', color=ink)
    fig.text(.055, .855, '24-hour job-mix experiment  |  Corrected power and planning  |  27 November 2026', fontsize=14, color=muted)
    ax = fig.add_axes([.085, .25, .62, .51], facecolor=bg)
    x = np.arange(len(cases))
    for i, (policy, name) in enumerate(zip(['THR', 'IQR', 'LR'], ['THR', 'IQR', 'Predictive (current)'])):
        values = [float(rows[c, policy]['energy_kwh']) for c in cases]
        bars = ax.bar(x + (i-1)*.24, values, .24, color=colors[i], label=name, zorder=3)
        for j, (bar, value) in enumerate(zip(bars, values)):
            ax.text(bar.get_x()+bar.get_width()/2, value+.32, f'{value:.3f}' if j==4 else f'{value:.2f}',
                    ha='center', va='bottom', fontsize=8.5 if j==4 else 10, color=ink)
    ax.set_ylim(0, 25)
    ax.set_yticks(np.arange(0, 26, 5))
    ax.set_ylabel('Energy (kWh)', labelpad=12, color=ink)
    ax.set_xticks(x, labels, color=muted)
    ax.tick_params(axis='both', length=0, pad=10, labelcolor=muted)
    for side in ['top', 'right', 'left']:
        ax.spines[side].set_visible(False)
    ax.spines['bottom'].set_color('#d7dfd9')
    ax.grid(axis='y', color='#dfe6df', zorder=0)
    ax.legend(loc='upper center', bbox_to_anchor=(.5, -.19), ncol=3, frameon=False, fontsize=13)
    fig.text(.755, .69, 'Prediction matches\nTHR on energy\nin all five cases.', color=ink,
             fontsize=23, weight='bold', linespacing=1.4)
    thr = float(rows['heavy', 'THR']['energy_kwh'])
    iqr = float(rows['heavy', 'IQR']['energy_kwh'])
    fig.text(.755, .43, f'Heavy case\nIQR uses {(thr-iqr)/thr*100:.2f}% less energy.\nAll three make 3 migrations.',
             fontsize=15, color=muted, linespacing=1.6)
    fig.text(.055, .09, 'Compare policies within each case: workload sizes differ. One fixed run per case; no seed averages.',
             fontsize=12, color=muted)
    fig.text(.055, .055, 'Research result, separate from PR2. Current predictor: frozen robust model with persistence fallback.',
             fontsize=11, color=muted)
    OUT.mkdir(parents=True, exist_ok=True)
    for extension in ['png', 'svg']:
        path = OUT / f'Energy_Comparison.{extension}'
        fig.savefig(path, dpi=160, facecolor=bg)
        print(path)
    plt.close(fig)


if __name__ == '__main__':
    main()
