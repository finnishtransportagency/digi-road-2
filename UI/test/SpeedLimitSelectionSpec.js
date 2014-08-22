/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var speedLimitsData = SpeedLimitsTestData.generate(1);
  var speedLimit = _.first(speedLimitsData);

  var selectSpeedLimit = function(map, speedLimitId) {
    var control = _.first(map.getControlsBy('displayClass', 'olControlSelectFeature'));
    var feature = _.find(testHelpers.getSpeedLimitFeatures(map), function(feature) {
      return feature.attributes.id === speedLimitId;
    });
    control.select(feature);
  };

  var clickElement = function(element) {
    var event = document.createEvent('MouseEvent');
    event.initMouseEvent('click', true, true, window, null, 0, 0, 0, 0, false, false, false, false, 0, null);
    element.dispatchEvent(event);
  };

  var endPointFeatures = function(features) {
    return _.filter(features, function(x) { return x.attributes.type === 'endpoint'; });
  };

  describe('when loading application with speed limit data', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimitsData));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      it('it displays speed limit segment ID in asset form', function() {
        expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
      });
      it('shows speed limit end point markers at both ends of one link speed limit segment', function() {
        var endPoints = endPointFeatures(testHelpers.getSpeedLimitFeatures(openLayersMap));
        var endPointCoordinates = _.map(_.pluck(endPoints, 'geometry'), function(geometry) { return {x: geometry.x, y: geometry.y}; });
        expect(endPointCoordinates).to.have.length(2);
        expect(endPointCoordinates[0]).to.deep.equal(speedLimit.points[0]);
        expect(endPointCoordinates[1]).to.deep.equal(speedLimit.points[1]);
      });

      describe('and zooming in', function() {
        before(function() {
          $('.pzbDiv-plus').click();
        });
        it('maintains speed limit selection', function() {
          expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
        });
      });

      describe('and clicking on the background map', function() {
        before(function() {
          var layer = $('.olLayerDiv').filter(function(i, e) { return _.contains($(e).attr('id'), 'OpenLayers_Layer_WMTS'); });
          clickElement(_.first(layer));
        });
        it('deselects speed limit', function() {
          expect($('#feature-attributes header')).not.to.exist;
        });
      });
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      describe('and selecting assets layer', function() {
        before(function() {
          $('.panel-header').filter(':visible').filter(function (i, element) {return _.contains($(element).text(), 'Joukkoliikenteen pysäkit'); }).click();
        });
        describe('and reselecting speed limits layer', function() {
          before(function() {
            $('.speed-limits').click();
          });
          it('deselects speed limit', function() {
            expect($('#feature-attributes header')).not.to.exist;
          });
        });
      });
    });
  });
});