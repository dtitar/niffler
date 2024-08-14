import {Box, Container, Grid, Typography, useMediaQuery, useTheme} from "@mui/material";
import {SpendingsTable} from "../../components/SpendingsTable";
import {ChangeEvent, useEffect, useState} from "react";
import {apiClient} from "../../api/apiClient.ts";
import {Statistic} from "../../types/Statistic.ts";
import {STAT_INITIAL_STATE} from "../../context/SessionContext.tsx";
import {convertValueToFilterPeriodValue, FilterPeriodValue} from "../../types/FilterPeriod.ts";
import {convertFilterPeriod} from "../../utils/dataConverter.ts";
import {convertCurrencyToData, Currency} from "../../types/Currency.ts";
import {Diagram} from "../../components/Diagram";

export const MainPage = () => {
    const [period, setPeriod] = useState<FilterPeriodValue>({label: "All time", value: "ALL"});
    const [stat, setStatistic] = useState<Statistic>(STAT_INITIAL_STATE);
    const [selectedCurrency, setSelectedCurrency] = useState<Currency>(
        {currency: "ALL"});
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
    const shouldRecise = useMediaQuery(theme.breakpoints.down('md'));

    const loadStats = () => {
        const currency = convertCurrencyToData(selectedCurrency);
        apiClient.getStat({
                onSuccess: (data) => {
                    setStatistic(data);
                },
                onFailure: (e) => console.error(e.message),
            },
            convertFilterPeriod(period),
            currency,
        );
    }

    useEffect(() => {
        loadStats();
    }, [period, selectedCurrency]);

    const handleChangePeriod = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        setPeriod(convertValueToFilterPeriodValue(e.target.value));
    }

    const handleChangeCurrency = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        setSelectedCurrency({currency: e.target.value});
    }

    return (
        <Container sx={{padding: isMobile ? 0 : "0 16px"}}>
            <Grid container
                  spacing={isMobile ? 0 : 3}
                  sx={{
                      margin: isMobile ? "22px auto" : "40px auto",
                      width: "100%",
                      padding: isMobile ? 0 : "0 16px",

                  }}>
                <Grid
                    item
                    xs={isMobile ? 12 : shouldRecise ? 5 : 3}
                    sx={{
                        padding: isMobile ? 0 : "0 16px",
                    }}
                >
                    <Typography
                        variant="h5"
                        component="h2"
                        sx={{
                            marginBottom: 2,
                        }}
                    >
                        Statistics
                    </Typography>
                    <Box sx={{
                        display: isMobile ? "flex": "block",
                    }}>
                        <Box sx={{
                            display: "flex",
                            alignItems: "center",
                            width: isMobile ? 150 : 220,
                            height: isMobile ? 150 : 220,
                        }}>
                            <Diagram stat={stat}/>
                        </Box>
                        <Box id="legend-container" sx={{
                            display: "flex",
                            justifyContent: "center",
                            alignItems: "center"
                        }}>
                        </Box>
                    </Box>
                </Grid>
                <Grid
                    item
                    xs={isMobile ? 12 : shouldRecise ? 7 : 9}
                    sx={{
                          padding: isMobile ? 0 : "0 16px",
                      }}
                >
                    <Typography
                        variant="h5"
                        component="h2"
                        sx={{
                            marginBottom: 2,
                        }}
                    >
                        History of Spendings
                    </Typography>
                    <SpendingsTable
                        period={period}
                        handleChangePeriod={handleChangePeriod}
                        selectedCurrency={selectedCurrency}
                        handleChangeCurrency={handleChangeCurrency}
                        onDeleteCallback={loadStats}
                    />
                </Grid>
            </Grid>
        </Container>
    )
}
