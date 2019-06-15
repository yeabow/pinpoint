import { ActionReducerMap, createSelector, createFeatureSelector } from '@ngrx/store';

import * as admin from './admin.reducer';
import * as agentSelectionForInfoPerServer from './agent-selection-for-info-per-server.reducer';
import * as agentSelectionForSideBar from './agent-selection-for-side-bar.reducer';
import * as applicationList from './application-list.reducer';
import * as dateFormat from './date-format.reducer';
import * as favoriteApplicationList from './favorite-application-list.reducer';
import * as inspectorChartHover from './inspector-chart-hover.reducer';
import * as loadChart from './load-chart.reducer';
import * as responseSummaryChart from './response-summary-chart.reducer';
import * as scatterChart from './scatter-chart.reducer';
import * as serverAndAgent from './server-and-agent.reducer';
import * as serverList from './server-list.reducer';
import * as timeline from './timeline.reducer';
import * as timezone from './timezone.reducer';
import * as transactionDetailData from './transaction-detail-data.reducer';
import * as transactionData from './transaction-info.reducer';
import * as targetList from './target-list.reducer';
import * as serverMap from './server-map.reducer';
import * as serverMapSelectedTarget from './server-map-selected-target.reducer';
import * as serverMapLoadingState from './server-map-loading-state.reducer';
import * as uiState from './ui-state.reducer';
import * as range from './range.reducer';
import * as chartLayout from './inspector-chart-layout-info.reducer';

export interface AppState {
    timeline: ITimelineInfo;
    timezone: string;
    dateFormat: number;
    loadChartYMax: number;
    responseSummaryChartYMax: number;
    agentSelection: string;
    agentSelectionForServerList: IAgentSelection;
    transactionData: ITransactionMetaData;
    transactionDetailData: ITransactionDetailData;
    hoverOnInspectorCharts: number;
    applicationList: IApplication[];
    favoriteApplicationList: IApplication[];
    serverList: any;
    scatterChart: IScatterData;
    serverMapData: any;
    serverMapLoadingState: string;
    serverMapTargetSelected: ISelectedTarget;
    serverMapTargetSelectByList: any;
    updateFilterOfServerAndAgentList: string;
    adminAgentList: { [key: string]: IAgent[] };
    uiState: IUIState;
    applicationInspectorChartLayout: IChartLayoutInfoResponse;
    agentInspectorChartLayout: IChartLayoutInfoResponse;
}

export const STORE_KEY = {
    TIMELINE: 'timeline',
    TIMEZONE: 'timezone',
    DATE_FORMAT: 'dateFormat',
    LOAD_CHART_Y_MAX: 'loadChartYMax',
    RESPONSE_SUMMARY_CHART_Y_MAX: 'responseSummaryChartYMax',
    AGENT_SELECTION: 'agentSelection',
    AGENT_SELECTION_FOR_SERVER_LIST: 'agentSelectionForServerList',
    TIMELINE_SELECTION_RANGE: 'timelineSelectionRange',
    TRANSACTION_DATA: 'transactionData',
    TRANSACTION_DETAIL_DATA: 'transactionDetailData',
    HOVER_ON_INSPECTOR_CHARTS: 'hoverOnInspectorCharts',
    APPLICATION_LIST: 'applicationList',
    FAVORITE_APPLICATION_LIST: 'favoriteApplicationList',
    SERVER_LIST: 'serverList',
    SCATTER_CHART: 'scatterChart',
    SERVER_MAP_DATA: 'serverMapData',
    SERVER_MAP_LOADING_STATE: 'serverMapLoadingState',
    SERVER_MAP_TARGET_SELECTED: 'serverMapTargetSelected',
    SERVER_MAP_TARGET_SELECTED_BY_LIST: 'serverMapTargetSelectByList',
    ADMIN_AGENT_LIST: 'adminAgentList',
    SERVER_AND_AGENT: 'serverAndAgent',
    UI_STATE: 'uiState',
    RANGE: 'range',
    APPLICATION_INSPECTOR_CHART_LAYOUT: 'applicationInspectorChartLayout',
    AGENT_INSPECTOR_CHART_LAYOUT: 'agentInspectorChartLayout',
};


export const reducers: ActionReducerMap<any> = {
    // [STORE_KEY.AGENT_INFo]: agentInfoReducer 방식은 빌드시 에러가 발생 함.
    agentSelection: agentSelectionForSideBar.Reducer,
    agentSelectionForServerList: agentSelectionForInfoPerServer.Reducer,
    applicationList: applicationList.Reducer,
    favoriteApplicationList: favoriteApplicationList.Reducer,
    dateFormat: dateFormat.Reducer,
    hoverOnInspectorCharts: inspectorChartHover.Reducer,
    loadChartYMax: loadChart.Reducer,
    responseSummaryChartYMax: responseSummaryChart.Reducer,
    scatterChart: scatterChart.Reducer,
    serverList: serverList.Reducer,
    serverMapLoadingState: serverMapLoadingState.Reducer,
    serverMapData: serverMap.Reducer,
    serverMapTargetSelected: serverMapSelectedTarget.Reducer,
    serverMapTargetSelectByList: targetList.Reducer,
    timezone: timezone.Reducer,
    transactionData: transactionData.Reducer,
    transactionDetailData: transactionDetailData.Reducer,
    adminAgentList: admin.Reducer,
    serverAndAgent: serverAndAgent.Reducer,
    uiState: uiState.Reducer,
    timeline: timeline.Reducer,
    range: range.Reducer,
    applicationInspectorChartLayout: chartLayout.ApplicationInspectorChartLayoutReducer,
    agentInspectorChartLayout: chartLayout.AgentInspectorChartLayoutReducer
};

export const Actions = {
    'ChangeTimezone': timezone.ChangeTimezone,
    'ChangeDateFormat': dateFormat.ChangeDateFormat,
    'ChangeResponseSummaryChartYMax': responseSummaryChart.ChangeResponseSummaryChartYMax,
    'ChangeLoadChartYMax': loadChart.ChangeLoadChartYMax,
    'ChangeAgent': agentSelectionForSideBar.ChangeAgent,
    'ChangeAgentForServerList': agentSelectionForInfoPerServer.ChangeAgentForServerList,
    'UpdateTransactionData': transactionData.UpdateTransactionData,
    'UpdateTransactionDetailData': transactionDetailData.UpdateTransactionDetailData,
    'ChangeHoverOnInspectorCharts': inspectorChartHover.ChangeHoverOnInspectorCharts,
    'UpdateApplicationList': applicationList.UpdateApplicationList,
    'AddFavoriteApplication': favoriteApplicationList.AddFavoriteApplication,
    'RemoveFavoriteApplication': favoriteApplicationList.RemoveFavoriteApplication,
    'UpdateServerList': serverList.UpdateServerList,
    'AddScatterChartData': scatterChart.AddScatterChartData,
    'UpdateServerMapData': serverMap.UpdateServerMapData,
    'UpdateServerMapLoadingState': serverMapLoadingState.UpdateServerMapLoadingState,
    'UpdateServerMapTargetSelected': serverMapSelectedTarget.UpdateServerMapTargetSelected,
    'UpdateServerMapSelectedTargetByList': targetList.UpdateServerMapSelectedTargetByList,
    'UpdateFilterOfServerAndAgentList': serverAndAgent.UpdateFilterOfServerAndAgentList,
    'UpdateAdminAgentList': admin.UpdateAdminAgentList,
    'ChangeServerMapDisableState': uiState.ChangeServerMapDisableState,
    'ChangeInfoPerServerVisibleState': uiState.ChangeInfoPerServerVisibleState,
    'UpdateTimelineData': timeline.UpdateTimelineData,
    'UpdateRange': range.UpdateRange,
    'UpdateApplicationInspectorChartLayout': chartLayout.UpdateApplicationInspectorChartLayoutInfo,
    'UpdateAgentInspectorChartLayout': chartLayout.UpdateAgentInspectorChartLayoutInfo
};

const getUI = createFeatureSelector('uiState');
export const selectServerMapDisableState = createSelector(
    getUI,
    (state: IUIState) => state['serverMap']
);
export const selectInfoPerServerVisibleState = createSelector(
    getUI,
    (state: IUIState) => state['infoPerServer']
);

const getTimeline = createFeatureSelector('timeline');
export const selectTimelineRange = createSelector(
    getTimeline,
    (state: ITimelineInfo) => state['range']
);
export const selectTimelineSelectionRange = createSelector(
    getTimeline,
    (state: ITimelineInfo) => state['selectionRange']
);
export const selectTimelineSelectedTime = createSelector(
    getTimeline,
    (state: ITimelineInfo) => state['selectedTime']
);

