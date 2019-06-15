import { Component, OnInit, OnChanges, Input, Output, EventEmitter, ViewChild, ElementRef, SimpleChanges, Renderer2, OnDestroy } from '@angular/core';
import bb, { Data, PrimitiveArray } from 'billboard.js';
import { Subject, merge, fromEvent } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';

import { MessageQueueService, MESSAGE_TO, GutterEventService, NewUrlStateNotificationService } from 'app/shared/services';
import { UrlPath } from 'app/shared/models';

export interface IChartConfig {
    dataConfig: Data;
    elseConfig: {[key: string]: any};
}

@Component({
    selector: 'pp-inspector-chart',
    templateUrl: './inspector-chart.component.html',
    styleUrls: ['./inspector-chart.component.css'],
})
export class InspectorChartComponent implements OnInit, OnChanges, OnDestroy {
    @ViewChild('chartHolder') chartHolder: ElementRef;
    @Input() chartConfig: IChartConfig;
    @Output() outRendered = new EventEmitter<void>(true);

    private unsubscribe = new Subject<void>();
    private chartInstance: any;
    private readonly inspectorChartRatio = 1.92;


    constructor(
        private messageQueueService: MessageQueueService,
        private gutterEventService: GutterEventService,
        private newUrlStateNotificationService: NewUrlStateNotificationService,
        private el: ElementRef,
        private renderer: Renderer2
    ) {}

    ngOnInit() {
        this.setHeight();
        merge(
            fromEvent(window, 'resize'),
            this.messageQueueService.receiveMessage(this.unsubscribe, MESSAGE_TO.INSPECTOR_CHART_SET_LAYOUT).pipe(),
            this.gutterEventService.onGutterResized$.pipe(
                filter((ratioArr: number[]) => !!ratioArr),
                map(([upperRatio]: number[]) => upperRatio),
                filter((ratio: number) => ratio >= 30 && ratio <= 55), // 30, 50: 차트가 포함되어 있는 split-area의 최소, 최대 사이즈(비율)
            )
        ).pipe(
            tap(() => this.setHeight()),
            filter(() => !!this.chartInstance),
        ).subscribe(() => {
            this.chartInstance.resize();
            this.setViewBox();
        });

        this.messageQueueService.receiveMessage(this.unsubscribe, MESSAGE_TO.CALL_TREE_ROW_SELECT).subscribe(([{time}]: [ISelectedRowInfo]) => {
            const closestTime = (Object.values(this.chartInstance.x())[0] as Date[])
                .map((d: Date) => d.getTime())
                .find((t: number, i: number, arr: number[]) => {
                    return time <= t || ((arr[i] < time && time < arr[i + 1]) && (time - arr[i] <= arr[i + 1] - time));
                });

            this.chartInstance.tooltip.show({
                x: closestTime
            });
        });
    }

    ngOnChanges(changes: SimpleChanges) {
        Object.keys(changes)
            .filter((propName: string) => {
                return changes[propName].currentValue;
            })
            .forEach((propName: string) => {
                const changedProp = changes[propName];

                switch (propName) {
                    case 'chartConfig':
                        this.chartInstance ? this.updateChart(changedProp) : this.initChart();
                        break;
                }
            });
    }

    ngOnDestroy() {
        this.unsubscribe.next();
        this.unsubscribe.complete();
    }

    private setHeight(): void {
        const width = window.getComputedStyle(this.chartHolder.nativeElement).getPropertyValue('width');
        const height = this.newUrlStateNotificationService.getStartPath() === UrlPath.INSPECTOR
            ? `${Number(width.replace(/px/, '')) / this.inspectorChartRatio}px`
            : `${this.el.nativeElement.offsetHeight}px`;

        this.renderer.setStyle(this.chartHolder.nativeElement, 'height', height);
    }

    private updateChart({previousValue, currentValue}: {previousValue: IChartConfig, currentValue: IChartConfig}): void {
        const {columns: prevColumns} = previousValue.dataConfig;
        const {columns: currColumns} = currentValue.dataConfig;
        const prevKeys = prevColumns.map(([key]: PrimitiveArray) => key);
        const currKeys = currColumns.map(([key]: PrimitiveArray) => key);
        const unloadedKeys = prevKeys.filter((key: string) => !currKeys.includes(key));
        const {axis: {y, y2 = {}}} = currentValue.elseConfig;

        this.chartInstance.load({
            columns: currColumns,
            unload: unloadedKeys,
        });

        this.chartInstance.axis.max({
            y: y.max,
            y2: y2.max
        });
    }

    private setViewBox(): void {
        const svg = this.el.nativeElement.querySelector('svg');
        const width = svg.getAttribute('width');
        const height = svg.getAttribute('height');

        this.renderer.setStyle(svg, 'width', `${width}px`);
        this.renderer.setStyle(svg, 'height', `${height}px`);
        this.renderer.setAttribute(svg, 'viewBox', `0 0 ${width} ${height}`);
        this.renderer.setAttribute(svg, 'preserveAspectRatio', `none`);
    }

    private initChart(): void {
        this.chartInstance = bb.generate({
            bindto: this.chartHolder.nativeElement,
            data: this.chartConfig.dataConfig,
            ...this.chartConfig.elseConfig,
            onrendered: () => this.finishLoading(),
        });

        const svg = this.el.nativeElement.querySelector('svg');

        this.renderer.setStyle(svg, 'transition', 'all ease 1s');
    }

    private finishLoading(): void {
        this.outRendered.emit();
    }
}
