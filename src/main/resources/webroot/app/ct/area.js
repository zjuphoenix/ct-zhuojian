/**
 * Created by wuhaitao on 2016/2/26.
 */
angular.module('ct.area', ['ui.router'])
    .config(function ($stateProvider) {
        $stateProvider.state('area', {
            url: '/area/:image',
            templateUrl: 'app/ct/area.html',
            controller: 'AreaCtrl'
        });
    })
    .controller('AreaCtrl', function($scope, $stateParams, $http) {
        var img = $stateParams.image;
        var x1,x2,y1,y2;

        $scope.myImage=img;
        $scope.myCroppedImage='';

        $scope.result = '';

        $scope.predictLesionType = function () {
            $http.post('/api/ct/predict',{
                "image":$scope.myImage,
                "x1":x1,
                "y1":y1,
                "x2":x2,
                "y2":y2
            }).then(function (result) {
                $scope.result = result.data.lesion;
            }, function (error) {
                console.log(error);
            });
        };

        $scope.areaChange = function(c) {
            x1 = c.x;
            y1 = c.y;
            x2 = c.x2;
            y2 = c.y2;
            angular.element(document.querySelector('#x1')).val(c.x);
            angular.element(document.querySelector('#y1')).val(c.y);
            angular.element(document.querySelector('#x2')).val(c.x2);
            angular.element(document.querySelector('#y2')).val(c.y2);
            angular.element(document.querySelector('#w')).val(c.w);
            angular.element(document.querySelector('#h')).val(c.h);
        }


        angular.element(document.querySelector('#target')).Jcrop({
            onChange:   $scope.areaChange
            /*onSelect:   $scope.areaChange,
            onRelease:  $scope.areaChange*/
        },function(){
        });

    });