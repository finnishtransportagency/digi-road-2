(function(root) {
  root.AssetFormElementsFactory = {
    construct: construct
  };

  function assetFormElementConstructor(className) {
    var assetFormElementConstructors = {
      prohibition: createProhibitionFormElements(),
      hazardousMaterialTransportProhibition: createHazardousMaterialTransportProhibitionFormElements(),
      winterSpeedLimits: PiecewiseLinearAssetFormElements.WinterSpeedLimitsFormElements,
      europeanRoads: PiecewiseLinearAssetFormElements.EuropeanRoadsFormElements,
      exitNumbers: PiecewiseLinearAssetFormElements.ExitNumbersFormElements,
      maintenance: createMaintenanceFormElements()
    };
    return assetFormElementConstructors[className] || PiecewiseLinearAssetFormElements.DefaultFormElements;
  }

  function construct(asset) {
    return assetFormElementConstructor(asset.layerName)(asset.unit, asset.editControlLabels, asset.className, asset.defaultValue, asset.possibleValues);
  }

  function createHazardousMaterialTransportProhibitionFormElements() {
    return ProhibitionFormElements([
      { typeId: 24, title: 'Ryhmän A vaarallisten aineiden kuljetus' },
      { typeId: 25, title: 'Ryhmän B vaarallisten aineiden kuljetus' }
    ], []);
  }

  function createProhibitionFormElements() {
    var prohibitionValues = [
      { typeId: 3, title: 'Ajoneuvo' },
      { typeId: 2, title: 'Moottoriajoneuvo' },
      { typeId: 23, title: 'Läpiajo' },
      { typeId: 12, title: 'Jalankulku' },
      { typeId: 11, title: 'Polkupyörä' },
      { typeId: 26, title: 'Ratsastus' },
      { typeId: 10, title: 'Mopo' },
      { typeId: 9, title: 'Moottoripyörä' },
      { typeId: 27, title: 'Moottorikelkka' },
      { typeId: 5, title: 'Linja-auto' },
      { typeId: 8, title: 'Taksi' },
      { typeId: 7, title: 'Henkilöauto' },
      { typeId: 6, title: 'Pakettiauto' },
      { typeId: 4, title: 'Kuorma-auto' },
      { typeId: 15, title: 'Matkailuajoneuvo' },
      { typeId: 19, title: 'Sotilasajoneuvo' },
      { typeId: 13, title: 'Ajoneuvoyhdistelmä' },
      { typeId: 14, title: 'Traktori tai maatalousajoneuvo' }
    ];
    var exceptionValues = [
      { typeId: 21, title: 'Huoltoajo' },
      { typeId: 22, title: 'Tontille ajo' },
      { typeId: 10, title: 'Mopo' },
      { typeId: 9, title: 'Moottoripyörä' },
      { typeId: 27, title: 'Moottorikelkka' },
      { typeId: 5, title: 'Linja-auto' },
      { typeId: 8, title: 'Taksi' },
      { typeId: 7, title: 'Henkilöauto' },
      { typeId: 6, title: 'Pakettiauto' },
      { typeId: 4, title: 'Kuorma-auto' },
      { typeId: 15, title: 'Matkailuajoneuvo' },
      { typeId: 19, title: 'Sotilasajoneuvo' },
      { typeId: 13, title: 'Ajoneuvoyhdistelmä' },
      { typeId: 14, title: 'Traktori tai maatalousajoneuvo' }
    ];

    return ProhibitionFormElements(prohibitionValues, exceptionValues);
  }

  function createMaintenanceFormElements(){
      var accessRightsValues = [
          { typeId: 1, title: 'Tieoikeus' },
          { typeId: 2, title: 'Tiekunnan osakkuus' },
          { typeId: 3, title: 'LiVin hallinnoimalla maa-alueella' },
          { typeId: 4, title: 'Huoltoreittikäytössä olevat kevyen liikenteen väylät (ei' +
              'rautatieliikennealuetta) väylä' },
          { typeId: 5, title: 'Tuntematon' }
      ];

      var maintenanceResponsibilityValues = [
          { typeId: 1, title: 'LiVi' },
          { typeId: 2, title: 'Muu' },
          { typeId: 0, title: 'Ei tietoa' }
      ];

    return MaintenanceFormElements(accessRightsValues, maintenanceResponsibilityValues);
  }
})(this);
