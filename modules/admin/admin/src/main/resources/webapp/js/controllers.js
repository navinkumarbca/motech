(function () {

    'use strict';

    /* Controllers */

    var adminModule = angular.module('motech-admin');

    adminModule.controller('BundleListCtrl', function($scope, Bundle, i18nService, $routeParams, $http) {

        var LOADING_STATE = 'LOADING';

        $scope.orderProp = 'name';
        $scope.invert = false;
        $scope.startUpload = false;
        $scope.versionOrder = ["version.major", "version.minor", "version.micro", "version.qualifier"];

        $scope.reloadPage = function () {
            location.reload();
        };

        $scope.bundlesWithSettings = [];
        $http({method:'GET', url:'../admin/api/settings/bundles/list'}).
            success(function (data) {
                $scope.bundlesWithSettings = data;
            });

        $scope.showSettings = function (bundle) {
            var symbolicName = bundle.symbolicName;

            if (symbolicName.indexOf('-bundle') === -1) {
                symbolicName = symbolicName + '-bundle';
            }

            return $.inArray(symbolicName, $scope.bundlesWithSettings) >= 0 || (bundle.settingsURL && bundle.settingsURL.length !== 0);
        };

        $scope.setOrder = function (prop) {
            if (prop === $scope.orderProp) {
                $scope.invert = !$scope.invert;
            } else {
                $scope.orderProp = prop;
                $scope.invert = false;
            }
        };

        $scope.getSortClass = function (prop) {
            var sortClass = "sorting-no";
            if (prop === $scope.orderProp) {
                if ($scope.invert) {
                    sortClass = "sorting-desc";
                } else {
                    sortClass = "sorting-asc";
                }
            }
            return sortClass;
        };

        $scope.bundles = Bundle.query();

        if ($routeParams.bundleId !== undefined) {
            $scope.bundle = Bundle.get({ bundleId:$routeParams.bundleId });
        }

        $scope.allBundlesCount = function () {
            if ($scope.bundles) {
                return $scope.bundles.length;
            } else {
                return 0;
            }
        };

        $scope.activeBundlesCount = function () {
            var count = 0;
            angular.forEach($scope.bundles, function (bundle) {
                count += bundle.isActive() ? 1 : 0;
            });

            return count;
        };

        $scope.installedBundlesCount = function () {
            var count = 0;
            angular.forEach($scope.bundles, function (bundle) {
                count += bundle.isInstalled() ? 1 : 0;
            });

            return count;
        };

        $scope.resolvedBundlesCount = function () {
            var count = 0;
            angular.forEach($scope.bundles, function (bundle) {
                count += bundle.isResolved() ? 1 : 0;
            });

            return count;
        };

        $scope.moduleSources = {
            'Repository':'Repository',
            'File':'File'
        };

        $scope.moduleSource = $scope.moduleSources.Repository;

        $scope.modules = {
            'org.motechproject:motech-demo:[0,)':'Demo',
            'org.motechproject:motech-message-campaign:[0,)':'Message campaign',
            'org.motechproject:motech-scheduletracking-api:[0,)':'Schedule Tracking',
            'org.motechproject:motech-alerts-api:[0,)':'Alerts',
            'org.motechproject:motech-appointments-api:[0,)':'Appointments',
            'org.motechproject:motech-cmslite-api:[0,)':'CMS Lite',
            'org.motechproject:motech-commcare-api:[0,)':'Commcare',
            'org.motechproject:motech-callflow:[0,)':'Call Flow',
            'org.motechproject:motech-event-aggregation-bundle:[0,)':'Event aggregation',
            'org.motechproject:motech-event-logging:[0,)':'Event logging',
            'org.motechproject:motech-tasks-bundle:[0,)':'Tasks',
            'org.motechproject:motech-pillreminder-api:[0,)':'Pill reminder',
            'org.motechproject:motech-outbox-bundle:[0,)':'Outbox'
        };

        $scope.module = "";

        $scope.stopBundle = function (bundle) {
            bundle.state = LOADING_STATE;
            bundle.$stop(dummyHandler, function (response) {
                bundle.state = 'RESOLVED';
                handleWithStackTrace('error', 'bundles.error.stop', response);
            });
        };

        $scope.startBundle = function (bundle) {
            bundle.state = LOADING_STATE;
            bundle.$start(dummyHandler, function (response) {
                bundle.state = 'RESOLVED';
                handleWithStackTrace('error', 'bundles.error.start', response);
            });
        };

        $scope.restartBundle = function (bundle) {
            bundle.state = LOADING_STATE;
            bundle.$restart(dummyHandler, function () {
                bundle.state = 'RESOLVED';
                motechAlert('bundles.error.restart', 'error');
            });
        };

        $scope.uninstallBundle = function (bundle) {
            jConfirm(jQuery.i18n.prop('bundles.uninstall.confirm'), jQuery.i18n.prop("confirm"), function (val) {
                if (val) {
                    var oldState = bundle.state;
                    bundle.state = LOADING_STATE;

                    blockUI();

                    bundle.$uninstall(function () {
                            // remove bundle from list
                            $scope.bundles.removeObject(bundle);
                            $scope.reloadPage();
                        },
                        function () {
                            motechAlert('bundles.error.uninstall', 'error');
                            bundle.state = oldState;
                            unblockUI();
                        });
                }
            });
        };

        $scope.getIconClass = function (bundle) {
            var cssClass = '';
            if (!bundle.isActive()) {
                cssClass = 'dullImage';
            }
            return cssClass;
        };

        $scope.bundleStable = function (bundle) {
            return bundle.state !== LOADING_STATE;
        };


        $scope.startOnUpload = function () {
            if ($scope.startUpload !== true) {
                $scope.startUpload = true;
                $('.start-on-upload').find('i').removeClass("icon-ban-circle").addClass('icon-ok');
            } else {
                $scope.startUpload = false;
                $('.start-on-upload').find('i').removeClass("icon-ok").addClass('icon-ban-circle');
            }
        };


        $scope.submitBundle = function () {
            blockUI();
            $('#bundleUploadForm').ajaxSubmit({
                success:function (data, textStatus, jqXHR) {
                    if (jqXHR.status === 0 && data) {
                        handleWithStackTrace('error', 'bundles.error.start', data);
                        unblockUI();
                    } else {
                        $scope.bundles = Bundle.query();
                        $scope.reloadPage();
                    }
    //              unblockUI();
                },
                error:function (response) {
                    handleWithStackTrace('error', 'bundles.error.start', response);
                    unblockUI();
                }
            });
        };

        Bundle.prototype.isActive = function () {
            return this.state === 'ACTIVE';
        };

        Bundle.prototype.printVersion = function () {
            var separator = '.',
            ver = this.version.major + separator + this.version.minor + separator + this.version.micro;
            if (this.version.qualifier) {
                ver += (separator + this.version.qualifier);
            }
            return ver;
        };


        Bundle.prototype.isInstalled = function () {
            return this.state === 'INSTALLED';
        };

        Bundle.prototype.isResolved = function () {
            return this.state === 'RESOLVED';
        };
    });

    adminModule.controller('StatusMsgCtrl', function($scope, $timeout, StatusMessage, i18nService, $cookieStore) {
        var UPDATE_INTERVAL = 1000 * 30,
        IGNORED_MSGS = 'ignoredMsgs',
        messageFilter = function (data) {
            var msgs = jQuery.grep(data, function (message, index) {
                return jQuery.inArray(message._id, $scope.ignoredMessages) === -1; // not in ignored list
            });
            $scope.messages = msgs;
        },
        update = function () {
            var i;
            StatusMessage.query(function (newMessages) {
                function messagesEqual(arg1, arg2) {
                    if (arg1.length !== arg2.length) {
                        return false;
                    }

                    for (i = arg1.length; i > 0; i-=1) {
                        if (arg1[i]._id !== arg2[i]._id) {
                            return false;
                        }
                    }

                    return true;
                }

                if (!messagesEqual(newMessages, $scope.messages)) {
                    messageFilter(newMessages);
                }
            });
        };

        $scope.ignoredMessages = $cookieStore.get(IGNORED_MSGS);
        $scope.messages = [];

        StatusMessage.query(function (data) {
            messageFilter(data);
        });

        $scope.getCssClass = function (msg) {
            var cssClass = 'msg';
            if (msg.level === 'ERROR') {
                cssClass += ' error';
            } else if (msg.level === 'OK') {
                cssClass += ' ok';
            }
            return cssClass;
        };

        $scope.printText = function (text) {
            var result = text;
            if (text.match(/^\{[\w\W]*\}$/)) {
                result = i18nService.getMessage(text.replace(/[\{\}]/g, ""));
            }
            return result;
        };

        $scope.refresh = function () {
            StatusMessage.query(function (data) {
                messageFilter(data);
            });
        };

        StatusMessage.prototype.getDate = function () {
            return new Date(this.date);
        };

        $scope.remove = function (message) {
            $scope.messages.removeObject(message);
            if ($scope.ignoredMessages === undefined) {
                $scope.ignoredMessages = [];
            }
            $scope.ignoredMessages.push(message._id);
            $cookieStore.put(IGNORED_MSGS, $scope.ignoredMessages);
        };

        $timeout(update, UPDATE_INTERVAL);
    });

    adminModule.controller('SettingsCtrl', function($scope, PlatformSettings, i18nService, $http) {

        $scope.platformSettings = PlatformSettings.query();

        $scope.label = function (key) {
            return i18nService.getMessage('settings.' + key);
        };

        $scope.saveSettings = function (settings) {
            blockUI();
            settings.$save(alertHandlerWithCallback('settings.saved', function () {
                $scope.platformSettings = PlatformSettings.query();
            }), jFormErrorHandler);
        };

        $scope.saveNewSettings = function () {
            blockUI();
            $('#noSettingsForm').ajaxSubmit({
                success:alertHandlerWithCallback('settings.saved', function () {
                    $scope.platformSettings = PlatformSettings.query();
                }),
                error:jFormErrorHandler
            });
        };

        $scope.uploadSettings = function () {
            $("#settingsFileForm").ajaxSubmit({
                success:alertHandlerWithCallback('settings.saved', function () {
                    $scope.platformSettings = PlatformSettings.query();
                }),
                error:jFormErrorHandler
            });
        };

        $scope.uploadActiveMqFile = function () {
            $("#activemqFileForm").ajaxSubmit({
                success:alertHandlerWithCallback('settings.saved', function () {
                    $scope.platformSettings = PlatformSettings.query();
                }),
                error:jFormErrorHandler
            });
        };

        $scope.uploadFileLocation = function () {
            $http({method:'POST', url:'../admin/api/settings/platform/location', params:{location:this.location}}).
                success(alertHandler('settings.saved', 'success')).
                error(alertHandler('settings.error.location'));
        };

        $scope.saveAll = function () {
            blockUI();
            $http.post('../admin/api/settings/platform/list', $scope.platformSettings).
                success(alertHandler('settings.saved', 'success')).
                error(alertHandler('settings.error.location'));
        };
    });

    adminModule.controller('ModuleCtrl', function($scope, ModuleSettings, Bundle, i18nService, $routeParams) {
        $scope.module = Bundle.details({ bundleId:$routeParams.bundleId });
    });

    adminModule.controller('BundleSettingsCtrl', function($scope, Bundle, ModuleSettings, $routeParams, $http) {
        $scope.moduleSettings = ModuleSettings.query({ bundleId:$routeParams.bundleId });

        $http.get('../admin/api/settings/' + $routeParams.bundleId + '/raw').success(function (data) {
            $scope.rawFiles = data;
        });

        $scope.module = Bundle.get({ bundleId:$routeParams.bundleId });

        $scope.saveSettings = function (mSettings, doRestart) {
            var successHandler;
            if (doRestart === true) {
                successHandler = restartBundleHandler;
            } else {
                successHandler = alertHandler('settings.saved', 'success');
            }

            blockUI();
            mSettings.$save({bundleId:$scope.module.bundleId}, successHandler, angularHandler('error', 'settings.error'));
        };

        $scope.uploadRaw = function (filename, doRestart) {
            var successHandler,
            id = '#_raw_' + filename.replace('.', '\\.');

            if (doRestart === true) {
                successHandler = restartBundleHandler;
            } else {
                successHandler = alertHandler('settings.saved', 'success');
            }

            blockUI();

            $(id).ajaxSubmit({
                success:successHandler,
                error:jFormErrorHandler
            });
        };

        var restartBundleHandler = function () {
            $scope.module.$restart(function () {
                unblockUI();
                motechAlert('settings.saved', 'success');
            }, alertHandler('bundles.error.restart', 'error'));
        };
    });

    adminModule.controller('OperationsCtrl', function($scope, $http) {
        $http({method:'GET', url:'../admin/api/mappings/graphite'}).
            success(function (data) {
                $scope.graphiteUrl = data;
                // prefix with http://
                if ($scope.graphiteUrl && $scope.graphiteUrl.lastIndexOf("http://") !== 0) {
                    $scope.graphiteUrl = "http://" + $scope.graphiteUrl;
                }
            });
    });

    adminModule.controller('ServerLogCtrl', function($scope, $http) {
        $scope.refresh = function () {
            blockUI();
            $http({method:'GET', url:'../admin/api/log'}).
                success(
                function (data) {
                    if (data === 'tomcat.error.logFileNotFound') {
                        $('#logContent').html($scope.msg(data));
                    } else {
                        $('#logContent').html(data.replace(/\r\n|\n/g, "<br/>"));
                        unblockUI();
                    }
                }).
                error(unblockUI());
        };

        //removing the sidebar from <body> before route change
        $scope.$on('$routeChangeStart', function(event, next, current) {
            $('div[id^="jquerySideBar"]').remove();
        });

        $scope.refresh();
    });

    adminModule.controller('ServerLogOptionsCtrl', function($scope, LogService, $http) {
        $scope.availableLevels = ['off', 'trace', 'debug', 'info', 'warn', 'error', 'fatal', 'all'];
        $scope.entry = {};

        $scope.config = LogService.get();

        $scope.save = function () {
            $scope.config.$save({}, alertHandlerWithCallback('log.changedLevel', function () {
                var loc = window.location.toString(), indexOf = loc.indexOf('#');
                window.location = loc.substring(0, indexOf) + "#/log";
            }), function () {
                motechAlert('log.changedLevelError', 'error');
            });
        };

        $scope.add = function () {
            $scope.config.loggers.push({
                logName:$scope.entry.name,
                logLevel:$scope.entry.level
            });

            delete $scope.entry.name;
            delete $scope.entry.level;
        };

        $scope.forAll = function (level) {
            var i;

            for (i = 0; i < $scope.config.loggers.length; i += 1) {
                $scope.config.loggers[i].logLevel = level;
            }

            $scope.config.root.logLevel = level;
        };

        $scope.change = function (logger, level) {
            $('#changeForAll .active').removeClass('active');
            logger.logLevel = level;
        };

        $scope.changeRoot = function (level) {
            $('#changeForAll .active').removeClass('active');
            $scope.config.root.logLevel = level;
        };

        $scope.changeEntry = function (value) {
            $('#changeForAll .active').removeClass('active');
            $scope.entry.level = value;
        };

        $scope.remove = function (logger) {
            $scope.config.loggers.removeObject(logger);

            if ($scope.config.trash === undefined || $scope.config.trash === null) {
                $scope.config.trash = [];
            }

            $scope.config.trash.push(logger);
        };

        $scope.validate = function () {
            if (!$scope.entry.name || $scope.entry.name.trim() === '' || $scope.entry.name === 'root') {
                return false;
            }

            if (!$scope.entry.level || $scope.entry.level.trim() === '') {
                return false;
            }

            return true;
        };

        $scope.levelsCss = function (level) {
            var cssClass = '';

            if (level !== undefined) {
                switch (level.toLowerCase()) {
                    case 'trace':
                        cssClass = 'btn-primary';
                        break;
                    case 'debug':
                        cssClass = 'btn-success';
                        break;
                    case 'info':
                        cssClass = 'btn-info';
                        break;
                    case 'warn':
                        cssClass = 'btn-warning';
                        break;
                    case 'error':
                        cssClass = 'btn-danger';
                        break;
                    case 'fatal':
                        cssClass = 'btn-inverse';
                        break;
                    default:
                        cssClass = '';
                        break;
                }
            }

            return cssClass;
        };
    });

    adminModule.controller('NotificationRuleCtrl', function($scope, NotificationRule, NotificationRuleDto, $location) {
        $scope.notificationRuleDto = new NotificationRuleDto();
        $scope.notificationRuleDto.notificationRules = NotificationRule.query();
        $scope.notificationRuleDto.idsToRemove = [];

        $scope.changeRuleActionType = function (notificationRule, actionType) {
            notificationRule.actionType = actionType;
        };

        $scope.saveRules = function (notificationRule) {
            notificationRule.$save();
        };

        $scope.removeRule = function (notificationRule) {
            $scope.notificationRuleDto.notificationRules.removeObject(notificationRule);
            if (notificationRule._id) {
                $scope.notificationRuleDto.idsToRemove.push(notificationRule._id);
            }
        };

        $scope.newRule = function () {
            var notificationRule = new NotificationRule();
            notificationRule.actionType = 'EMAIL';

            $scope.notificationRuleDto.notificationRules.push(notificationRule);
        };

        $scope.save = function () {
            $scope.notificationRuleDto.$save(function () {
                motechAlert('messages.notifications.saved', 'success');
                $location.path('messages');
            }, angularHandler('error', 'messages.notifications.errorSave'));
        };
    });

    adminModule.controller('QueueStatisticsCtrl', function($scope, $http) {

        $scope.dataAvailable = true;

        $http.get('../admin/api/queues/').success(function (data) {
            $scope.queues = data;
        }).error(function () {
                $scope.dataAvailable = false;
            });

    });

    adminModule.controller('MessageStatisticsCtrl', function($scope, $http, $routeParams) {

        var queue = $routeParams.queueName;

        $scope.dataAvailable = true;

        $http.get('../admin/api/queues/browse?queueName=' + queue).success(function (data) {
            $scope.messages = data;
        }).error(function () {
                $scope.dataAvailable = false;
            });


    });
}());
