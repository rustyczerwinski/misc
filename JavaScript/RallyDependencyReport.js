<!DOCTYPE html>
<html>
<head>
    <title>Adapted Portfolio Hierarchy App</title>

    <script type="text/javascript" src="/apps/2.0rc1/sdk-debug.js"></script>

    <script type="text/javascript">
        Rally.onReady(function() {
        	
        /**********************
        //BEGIN APP BUILD
        **********************/
        window.console && console.info('test');
        
            Ext.define('CustomApp', {
                extend: 'Rally.app.App',
                componentCls: 'app',
                items: [
                        { 
                        	xtype: 'panel', 
                        	itemId: 'optionsDiv', 
                        	id: 'optionsDivID',
                        	bodyPadding: 10,
                        	autoScroll: true,
                        	border: false,
                        	layout: {
                        		type: 'hbox',
                        		align: 'left'
                        	},
                        	items: [
								{ xtype: 'panel', itemId: 'releaseDiv', border: false, bodyPadding: '0 0 0 10'},
								{ xtype: 'panel', itemId: 'launchButtonDiv', border: false, bodyPadding: '0 0 0 10' }
                        	]
                        },
                        { 
                        	xtype: 'panel', 
                        	itemId: 'rightData',
                        	id: 'rightDataID',
                        	border: false,
                        	margin:'0 0 0 10',
                        	bodyPadding: 10,
                        	layout: {
                        		type: 'vbox',
                        		align: 'top'
                        	},
                        	items: [{ xtype: 'panel', itemId: 'piCumulativeFlowDiv', border: false, bodyPadding: 5 }]
                        },
                        { 
                        	xtype: 'panel', 
                        	itemId: 'resultsDiv2',
                        	bodyPadding: 10,
                        	height: 400,
                        	layout: {
                        		type: 'hbox',
                        		align: 'left'
                        	},
                        	items: [{ xtype: 'panel', itemId: 'piLeafStoriesDiv', border: false, bodyPadding: 5 }]
                        }
                ],

                launch: function() {
                	this.enteredRelease = '';
                	this.down('#releaseDiv').add({
                		xtype: 'rallyreleasecombobox',
                		itemId: 'releaseBox',
                		name: 'release',
                		width: 'auto',
                		defaultToCurrentTimebox: false,
                		showArrows: false,
                		allowNoEntry: false,
                		allowUnscheduled: true,
                		fieldLabel: 'Filter by This Release'
                	});
                	
                	this.down('#launchButtonDiv').add({
                		xtype: 'rallybutton',
                		text: 'Run for selected criteria',
                		scope: this,
                		handler: function() {
                            this._onButtonClicked();
                        }
                	});
                },
				
                _onButtonClicked: function() {
                	this.enteredRelease = this.down('#releaseDiv').items.items[0].lastSelection[0].data.Name;
                	this.enteredReleaseOID = this.down('#releaseDiv').items.items[0].lastSelection[0].data.ObjectID;
                	this.releaseStart = this.down('#releaseDiv').items.items[0].lastSelection[0].data.ReleaseStartDate;
                	this.releaseEnd = this.down('#releaseDiv').items.items[0].lastSelection[0].data.ReleaseDate;
                	if (this.down('#picfd') !== null) { this.down('#picfd').destroy(); }
                	if (this.down('#piLSR') !== null) { this.down('#piLSR').destroy(); }
                    var appWidth = Ext.get('optionsDivID').dom.clientWidth;
                    Ext.getCmp('rightDataID').setWidth(appWidth-35);
                    this._getReleaseOIDs();
                },
                
                _getReleaseOIDs: function(){
                	this.releaseArray = new Array();
                	var currentProject = this.getContext().map.map.project.OID;
                	var currentScopeUp = this.getContext().map.map.projectScopeUp;
                	var currentScopeDown = this.getContext().map.map.projectScopeDown;
                	
                	Ext.create('Rally.data.WsapiDataStore', {
                        model: 'Release',
                        fetch: true,
                        autoLoad: true,
                        pageSize: 100,
                        filters: [{
                            property: 'Name',
                            value: this.enteredRelease
                        }],
                        context: {
                            project: '/project/'+currentProject,
                            projectScopeUp: currentScopeUp,
                            projectScopeDown: currentScopeDown
                        },
                        sorters: [
                            {
                                property: 'ObjectID',
                                direction: 'ASC'
                            }
                        ],
                        listeners: {
                        	scope: this,
                            load: function(store, data, success) {
                                store.each(function(record){
                                	this.releaseArray.push(record.data);
                                },this);
                                if (store.currentPage ? (store.totalCount > store.currentPage*store.totalCount) : (store.totalCount > store.pageSize)){
                                	this._getNextPage(store);
                                }
                                else {
                                	this._buildPICumulativeFlow();
                                }
                            }
                        }
                    });
                },
                
                _buildPICumulativeFlow: function(){
                	//Determine chart start/end dates
                	var today = new Date();
                	
                	Ext.define('My.BurnUpCalculator', {
                	    extend: 'Rally.data.lookback.calculator.TimeSeriesCalculator',
                	    endDate: today,
                	    getMetrics: function () {
                	        return [
                	            {
                	                as: 'Backlog',
                	                f: 'filteredCount',
                	                filterField: 'ScheduleState',
                	                filterValues: ['Backlog']
                	            },
                	            {
                	                as: 'Defined',
                	                f: 'filteredCount',
                	                filterField: 'ScheduleState',
                	                filterValues: ['Defined']
                	            },
                	            {
                	                as: 'InProgress',
                	                f: 'filteredCount',
                	                filterField: 'ScheduleState',
                	                filterValues: ['In-Progress']
                	            },
                	            {
                	                as: 'Completed',
                	                f: 'filteredCount',
                	                filterField: 'ScheduleState',
                	                filterValues: ['Completed']
                	            },
                	            {
                	                as: 'Accepted',
                	                f: 'filteredCount',
                	                filterField: 'ScheduleState',
                	                filterValues: ['Accepted']
                	            },
                	        ];
                	    }
                	});
                	
                	if (this.releaseStart !== null){
                		var startDate = this.releaseStart;
                	}
                	else{
                		var startDate = null;
                	}
                	if (this.releaseEnd !== null){
                		var endDate = this.releaseEnd;
                	}
                	else{
                		var endDate = today;
                	}
                	var width = Ext.get('optionsDivID').dom.clientWidth;
                	
                	var releaseOIDstring = {"$in":[]};
                	for (var a=0; a<this.releaseArray.length; a++){
                		releaseOIDstring.$in.push(this.releaseArray[a].ObjectID);
                	}
                	var find = {
            				_TypeHierarchy: 'HierarchicalRequirement',
                            Release: releaseOIDstring,
                            Children: null
            		};
            		var titleText = 'Release Cumulative Flow Diagram';
            		this._postCumulativeFlowChart(find,titleText,width,startDate,endDate);
                },
                
                _postCumulativeFlowChart: function(find,titleText,width,startDate,endDate){
                	this.down('#piCumulativeFlowDiv').add({
                		xtype: 'rallychart',
                		itemId: 'picfd',
                		width: width-70,
                		height: 400,
                        storeType: 'Rally.data.lookback.SnapshotStore',
                        storeConfig: {
                            find: find,
                            fetch: ['ScheduleState', 'PlanEstimate'],
                            hydrate: ['ScheduleState']
                        },
                        calculatorType: 'My.BurnUpCalculator',
                        calculatorConfig: {
                        	startDate: startDate,
                        	endDate: endDate
                        },
                        chartConfig: {
                            chart: {
                            	type: 'area',
                                zoomType: 'xy'
                            },
                            plotOptions: {
                            	area: {
                                	stacking: 'normal',
                                	marker: {
                                		enabled: false
                                	}
                            	}
                            },
                            title: {
                                text: titleText
                            },
                            xAxis: {
                                tickmarkPlacement: 'on',
                                tickInterval: 20,
                                title: {
                                    text: 'Time'
                                }
                            },
                            yAxis: [
                                {
                                    title: {
                                        text: 'Story Count'
                                    }
                                }
                            ],
                        }
                	});
                    this._buildPILeafStoryReadout();
                },
                
                _buildPILeafStoryReadout: function(){
                	this.leafStoryStoreArray = new Array();
                	
                	//-----Filter by Tag, if necessary-----//
                		var filters = [{
                            property: 'Release.Name',
                            value: this.enteredRelease
                        },
                        {
                        	property: 'DirectChildrenCount',
                        	value: 0
                        }];
                	
                	var currentProject = this.getContext().map.map.project.OID;
                   	var currentScopeUp = this.getContext().map.map.projectScopeUp;
                   	var currentScopeDown = this.getContext().map.map.projectScopeDown;

                	Ext.create('Rally.data.WsapiDataStore', {
                        model: 'hierarchicalrequirement',
                        fetch: true,
                        autoLoad: true,
                        pageSize: 100,
                        filters: filters,
                        context: {
                            project: '/project/'+currentProject,
                            projectScopeUp: currentScopeUp,
                            projectScopeDown: currentScopeDown
                        },
                        sorters: [
                            {
                                property: 'FormattedID',
                                direction: 'ASC'
                            }
                        ],
                        listeners: {
                        	scope: this,
                            load: function(store, data, success) {
	                            this.FeatureArray = new Array();
                                store.each(function(record){
                                	record.data.OwnerName = record.data.Owner !== null ? record.data.Owner._refObjectName : null;
                                	record.data.IterationName = record.data.Iteration !== null ? record.data.Iteration._refObjectName : null;
                                	record.data.ReleaseName = record.data.Release !== null ? record.data.Release._refObjectName : null;
                                	record.data.FeatureName = record.data.Feature !== null ? record.data.Feature._refObjectName : null;
									window.console && console.info(record.data.Feature !== null ? record.data.Feature._refObjectName : "No Feature");
									if (record.data.Feature !== null !== null) { 
										this.FeatureArray.push(record.data.Feature.FormattedID);
										window.console && console.log(record.data.Feature.FormattedID);
										}
                              	this.leafStoryStoreArray.push(record.data);
                                },this);
                                if (store.currentPage ? (store.totalCount > store.currentPage*store.pageSize) : (store.totalCount > store.pageSize)){
                                	this._getNextPage(store);
                                }
                                else {
                                	this._processLeafStories();
                                }
                            }
                        }
                    });
                },
                
                _processLeafStories: function(){
                	
                	//window.console && console.log(this.leafStoryStoreArray);
                	 var store = Ext.create('Rally.data.custom.Store', {
                	     autoLoad: true,
                	     pageSize: 20000,
                	     data: this.leafStoryStoreArray
                	 });
                	var appWidth = Ext.get('optionsDivID').dom.clientWidth;
                	this.down('#piLeafStoriesDiv').add({
                		xtype: 'rallygrid',
                		itemId: 'piLSR',
                    	store: store,
                    	width: (appWidth-35),
                    	//width: 600,
                    	height: 350,
                    	autoScroll: true,
                    	autoShow: true,
                    	enableEditing: false,
                    	showPagingToolbar: false,
                    	columnCfgs: [
                    	        {
                    	            text: "ID",
                    	            dataIndex: "FormattedID",
                    	            width: 75
                    	        },{
                    	            text: "Name",
                    	            dataIndex: "Name",
                    	            width: 250
                    	        },{
                    	            text: "State",
                    	            dataIndex: "ScheduleState",
                    	            width: 75
                    	        },{
                    	        	text: "Kanban State",
                    	        	dataIndex: "c_DGGSKanbanState",
                    	        	width: 75
                    	        },{
                    	        	text: "Owner",
                    	        	dataIndex: "OwnerName",
                    	        	width: 100
                    	        },{
                    	        	text: "Release",
                    	        	dataIndex: "ReleaseName",
                    	        	width: 125
                    	        },{
                    	        	text: "Story Points",
                    	        	dataIndex: "PlanEstimate",
                    	        	width: 50
                    	        },{
                    	        	text: "Blocked?",
                    	        	dataIndex: "Blocked",
                    	        	width: 50
                    	        }
                    	]
                	});
                },
                
                _getNextPage: function(store){
                	store.nextPage();
                },
                
                roundNumber: function(number,decimal_points) {
                	if(!decimal_points) return Math.round(number);
                	if(number == 0) {
                		var decimals = "";
                		for(var i=0;i<decimal_points;i++) decimals += "0";
                		return "0."+decimals;
                	}

                	var exponent = Math.pow(10,decimal_points);
                	var num = Math.round((number * exponent)).toString();
                	return num.slice(0,-1*decimal_points) + "." + num.slice(-1*decimal_points)
                }
            });

            Rally.launchApp('CustomApp', {
                name: 'Team Release Status'
            });
        });
    </script>

    <style type="text/css">
        .app {
            margin: 20px;
        }
    </style>
</head>
<body>
</body>
</html>